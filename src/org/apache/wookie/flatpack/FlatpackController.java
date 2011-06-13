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
package org.apache.wookie.flatpack;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.wookie.beans.IWidgetInstance;
import org.apache.wookie.controller.Controller;
import org.apache.wookie.controller.WidgetInstancesController;
import org.apache.wookie.exceptions.InvalidParametersException;
import org.apache.wookie.exceptions.UnauthorizedAccessException;
import org.apache.wookie.helpers.WidgetKeyManager;

/**
 * Controller for the Flatpack/exports API
 * 
 * This class provides a controller front end for the FlatpackFactory class, enabling the export of Widget Instances via a HTTP POST request.
 * 
 * POST /flatpack/ {params: api_key, instance_params OR id_key} creates a new W3C Widget package (.wgt) with an opaque file name for the specified widget instance, and returns the download URL. 
 * If an invalid API key is supplied, a 401 error code is returned. If no instance can be found, or the parameters supplied are invalid, a 400 error code is returned.
 */
public class FlatpackController extends Controller {

	private static final long serialVersionUID = 2907712805939515004L;
	static Logger _logger = Logger.getLogger(FlatpackController.class.getName());	

	/**
	 * Deny access to the listing of the flatpack folder
	 */
	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		response.sendError(HttpServletResponse.SC_FORBIDDEN);
	}

	/**
	 * We override the default POST method from Controller as we need to return the package URL in the Response to the client
	 */
	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		try {
			response.getWriter().write(createFlatpack(request));
			response.setStatus(HttpServletResponse.SC_OK);
		} catch (InvalidParametersException e){
			_logger.error(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_BAD_REQUEST); 
		} catch (UnauthorizedAccessException e){
			_logger.error(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}

	/**
	 * Create a flatpack for the request
	 * @param request
	 * @return the URL for the flatpack
	 * @throws UnauthorizedAccessException if there is no valid API key supplied
	 * @throws InvalidParametersException if there is no valid widget instance
	 */
	private String createFlatpack(HttpServletRequest request) throws UnauthorizedAccessException, InvalidParametersException{
		if (!WidgetKeyManager.isValidRequest(request)) throw new UnauthorizedAccessException();
		String path;
		try {
			IWidgetInstance instance = WidgetInstancesController.findWidgetInstance(request);
			FlatpackFactory fac = new FlatpackFactory(instance);
			// Set the folder to save the flatpack to an appropriate location on this server
			fac.setFlatpackFolder(new File(request.getSession().getServletContext().getRealPath(FlatpackFactory.DEFAULT_FLATPACK_FOLDER.getPath())));
			// Construct the URL to the file
			String serverName = request.getSession().getServletContext().getContextPath();
			path = serverName + "/" + FlatpackFactory.DEFAULT_FLATPACK_FOLDER + "/" + fac.pack().getName();
			URL url =  new URL(request.getScheme() , request.getServerName() , request.getServerPort() , path);
			path = url.toString();
		} catch (Exception e) {
			throw new InvalidParametersException();
		}
		return path;
	}


}