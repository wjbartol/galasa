/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.framework.auth.spi;

import javax.servlet.ServletException;

public interface IAuthServiceFactory {
    IAuthService getAuthService() throws ServletException;
}
