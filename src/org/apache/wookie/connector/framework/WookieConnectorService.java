/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wookie.connector.framework;

/**
 * An implementation of the WookieConnectorService for use by Wookie itself.
 * 
 */
public class WookieConnectorService extends AbstractWookieConnectorService {
  User currentUser;
  String userLogin;
 
  /**
   * Create a connector service used locally within Wookie.
   * Since the Wookie server does not currently support multiple users we provide
   * a userLogin to allows us to simulate multiple users during widget demonstration.
   * 
   * @param url
   * @param apiKey
   * @param sharedDataKey
   * @param userLogin - the user login to use for this connection
   * @throws WookieConnectorException
   */
  public WookieConnectorService(String url, String apiKey, String sharedDataKey, String userLogin) throws WookieConnectorException {
    setConnection(new WookieServerConnection(url, apiKey, sharedDataKey));
    this.userLogin = userLogin;
    currentUser = getUser(userLogin);
  }
  
  public User getCurrentUser() {
    return currentUser;
  }

  private User getFirstTestUser() {
    return new User("testuser", "First Test User");
  }

  private User getSecondTestUser() {
    return new User("testuser2", "Second Test User");
  }
  
  public User getUser(String login) {
    if (login.toLowerCase().equals("testuser")) {
      return getFirstTestUser();
    } else if (login.toLowerCase().equals("testuser2")) {
      return getSecondTestUser();
    }
    return null;
  }
  

}
