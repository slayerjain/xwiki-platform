/*
 * Copyright 2007, XpertNet SARL, and individual contributors as indicated
 * by the contributors.txt.
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
package com.xpn.xwiki.it;

/**
 * Verify the login and logout features of XWiki.
 *
 * @version $Id: $
 */
public class LoginTest extends AbstractSeleniumTestCase
{
    public void setUp() throws Exception
    {
        super.setUp();

        // Verify that the user isn't logged in
        if (isAuthenticated()) {
            logout();
        }

        goToLoginPage();
    }

    public void testLogAsAdmin()
    {
        setFieldValue("j_username", "Admin");
        setFieldValue("j_password", "admin");
        checkField("rememberme");
        submit();

        assertTrue("Admin user has not been authenticated", isAuthenticated());
    }

    public void testLogWithWrongPassword()
    {
        setFieldValue("j_username", "Admin");
        setFieldValue("j_password", "wrong password");
        submit();

        assertTextPresent("Wrong password");
    }

    public void testLogWithInvalidUsername()
    {
        setFieldValue("j_username", "non existent user");
        setFieldValue("j_password", "admin");
        submit();

        assertTextPresent("Wrong user name");
    }
}
