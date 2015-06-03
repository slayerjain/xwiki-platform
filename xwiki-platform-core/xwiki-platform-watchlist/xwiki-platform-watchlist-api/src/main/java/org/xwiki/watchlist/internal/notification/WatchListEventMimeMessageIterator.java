/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.watchlist.internal.notification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.xwiki.mail.MimeMessageFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.watchlist.internal.UserAvatarAttachmentExtractor;
import org.xwiki.watchlist.internal.api.WatchListEvent;

import com.xpn.xwiki.api.Attachment;

/**
 * Generate {@link MimeMessage}s from {@link WatchListMessageData}s extracted by an iterator over a list of subscribers.
 *
 * @version $Id$
 * @since 7.1M1
 */
public class WatchListEventMimeMessageIterator implements Iterator<MimeMessage>, Iterable<MimeMessage>
{
    /**
     * XWiki User Class first name property name.
     */
    public static final String XWIKI_USER_CLASS_FIRST_NAME_PROP = "first_name";

    /**
     * XWiki User Class last name property name.
     */
    public static final String XWIKI_USER_CLASS_LAST_NAME_PROP = "last_name";

    /**
     * Template factory "attachments" parameter.
     */
    public static final String TEMPLATE_FACTORY_ATTACHMENTS_PARAMETER = "attachments";

    private MimeMessageFactory<MimeMessage> factory;

    private Iterator<WatchListMessageData> subscriberIterator;

    private Map<String, Object> parameters;

    private Map<String, Object> factoryParameters;

    private UserAvatarAttachmentExtractor avatarExtractor;

    private List<Attachment> originalTemplateExtraParameters;

    /**
     * @param subscriberIterator the iterator used to go through each subscriber and extract the
     *            {@link WatchListMessageData}
     * @param factory the factory to use to create a single MimeMessage
     * @param parameters the parameters from which to extract the factory source and factory parameters
     * @param avatarExtractor the {@link UserAvatarAttachmentExtractor} used once per message to extract an author's
     *            avatar attachment
     */
    public WatchListEventMimeMessageIterator(Iterator<WatchListMessageData> subscriberIterator,
        MimeMessageFactory<MimeMessage> factory, Map<String, Object> parameters,
        UserAvatarAttachmentExtractor avatarExtractor)
    {
        this.subscriberIterator = subscriberIterator;
        this.factory = factory;
        this.parameters = parameters;
        this.avatarExtractor = avatarExtractor;

        this.factoryParameters =
            (Map<String, Object>) this.parameters.get(WatchListEventMimeMessageFactory.PARAMETERS_PARAMETER);

        // Save the list of attachments initially provided by the caller, since we will be constantly updating the
        // template factory's parameters for each message and we want to remember these to apply them on each message.
        this.originalTemplateExtraParameters =
            (List<Attachment>) this.factoryParameters.get(TEMPLATE_FACTORY_ATTACHMENTS_PARAMETER);
    }

    @Override
    public Iterator<MimeMessage> iterator()
    {
        return this;
    }

    @Override
    public boolean hasNext()
    {
        return this.subscriberIterator.hasNext();
    }

    @Override
    public MimeMessage next()
    {
        MimeMessage message;
        WatchListMessageData watchListMessageData = this.subscriberIterator.next();

        try {
            // Update the values for this new message.
            updateFactoryParameters(factoryParameters, watchListMessageData);

            DocumentReference factorySource = watchListMessageData.getTemplateReference();

            // Use the factory to create the message.
            message = this.factory.createMessage(factorySource, factoryParameters);
            message.addRecipient(Message.RecipientType.TO, watchListMessageData.getAddress());
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to create Mime Message, aborting mail sending for this batch", e);
        }

        return message;
    }

    /**
     * Update the factory's parameters with the values specific to the message we are going to send right now.
     *
     * @param watchListMessageData the messageData to use for the new message
     * @param factoryParameters the factory parameters we wish to update
     */
    private void updateFactoryParameters(Map<String, Object> factoryParameters,
        WatchListMessageData watchListMessageData)
    {
        Map<String, Object> velocityVariables = (Map<String, Object>) factoryParameters.get("velocityVariables");

        // Set the list of events, containing 1 event per document.
        List<WatchListEvent> events = watchListMessageData.getEvents();
        velocityVariables.put("events", events);

        // Compute the list of modified documents.
        List<String> modifiedDocuments = new ArrayList<>();
        for (WatchListEvent event : events) {
            if (!modifiedDocuments.contains(event.getPrefixedFullName())) {
                modifiedDocuments.add(event.getPrefixedFullName());
            }
        }
        velocityVariables.put("modifiedDocuments", modifiedDocuments);

        velocityVariables.put(XWIKI_USER_CLASS_FIRST_NAME_PROP, watchListMessageData.getFirstName());
        velocityVariables.put(XWIKI_USER_CLASS_LAST_NAME_PROP, watchListMessageData.getLastName());

        // Attach the avatars of the authors of the events we are notifying about.
        if (parameters.get(WatchListEventMimeMessageFactory.ATTACH_AUTHOR_AVATARS_PARAMETER) == Boolean.TRUE) {
            List<Attachment> templateExtraAttachments = getTemplateExtraAttachments(factoryParameters, events);
            factoryParameters.put(TEMPLATE_FACTORY_ATTACHMENTS_PARAMETER, templateExtraAttachments);
        }

    }

    private List<Attachment> getTemplateExtraAttachments(Map<String, Object> factoryParameters,
        List<WatchListEvent> events)
    {
        // Append to any existing list of extra attachments specified by the caller.
        List<Attachment> templateExtraAttachments = new ArrayList<>();
        if (originalTemplateExtraParameters != null) {
            templateExtraAttachments.addAll(originalTemplateExtraParameters);
        }

        Set<DocumentReference> processedAuthors = new HashSet<DocumentReference>();
        for (WatchListEvent event : events) {
            for (DocumentReference authorReference : event.getAuthorReferences()) {
                // TODO: We do a minimal performance improvement here by not extracting a user's avatar twice for the
                // same message, but it should probably be the UserAvatarExtractor's job to support some caching
                // instead since that would also work across messages and would be much more useful.
                if (!processedAuthors.contains(authorReference)) {
                    Attachment avatarAttachment = avatarExtractor.getUserAvatar(authorReference);
                    if (avatarAttachment != null) {
                        templateExtraAttachments.add(avatarAttachment);
                    }
                    processedAuthors.add(authorReference);
                }
            }
        }
        return templateExtraAttachments;
    }

    @Override
    public void remove()
    {
        throw new UnsupportedOperationException("remove");
    }
}
