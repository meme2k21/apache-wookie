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
package org.apache.wookie.w3c.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.wookie.w3c.util.LocalizationUtils;
import org.apache.wookie.w3c.IContentEntity;
import org.apache.wookie.w3c.W3CWidget;
import org.apache.wookie.w3c.IStartPageProcessor;
import org.apache.wookie.w3c.IW3CXMLConfiguration;
import org.apache.wookie.w3c.exceptions.BadManifestException;
import org.apache.wookie.w3c.exceptions.BadWidgetZipFileException;
import org.apache.wookie.w3c.exceptions.InvalidStartFileException;
import org.apache.wookie.w3c.impl.WidgetManifestModel;

/**
 * Utilities for working with Widget packages, i.e. Zip Files with an XML manifest
 * @author scott
 *
 */
public class WidgetPackageUtils {
	static Logger _logger = Logger.getLogger(WidgetPackageUtils.class.getName());
	
	/**
	 * Implements the rule for finding a file within a widget; it returns the first localized version 
	 * first before returning a root version. An exception is thrown if the path points to a folder, and
	 * null is returned if no file is found
	 * @param path
	 * @param locales the supported locales, in list of preference
	 * @param zip
	 * @return
	 * @throws Exception
	 */
	public static String locateFilePath(String path, String[] locales, ZipFile zip) throws Exception{
		String[] paths = locateFilePaths(path, locales, zip);
		if (paths != null && paths.length != 0) return paths[0];
		return null;
	}
	
	/**
	 * Returns the set of valid file paths for a given resource. All valid paths are returned, starting
	 * with localized versions for supported locales before the root version (if present).
	 * @param path
	 * @param locales
	 * @param zip
	 * @return
	 * @throws Exception
	 */
	public static String[] locateFilePaths(String path, String[] locales, ZipFile zip) throws Exception{
		ArrayList<String> paths = new ArrayList<String>();
		if (path.startsWith("/")) path = path.substring(1, path.length());
		String[] pathComponents = path.split("/");
		if ("locales".equalsIgnoreCase(pathComponents[0])){
			if (pathComponents.length < 2) return null;
			if (!LocalizationUtils.isValidLanguageTag(pathComponents[1])) return null;
		}
		// Look in localized folders first
		for (String locale:locales){
			String localePath = "locales/"+locale.trim()+"/"+path;
			if (zip.getEntry(localePath) != null){
				if (zip.getEntry(localePath).isDirectory()) throw new Exception();
				paths.add(localePath);
			}
		}
		// Look in root folder
		if (zip.getEntry(path) != null && !zip.getEntry(path).isDirectory()) paths.add(path);
		return (String[]) paths.toArray(new String[paths.size()]);
	}
	
	/**
	 * Return the language tag for a given path
	 * @param path
	 * @return
	 */
	public static String languageTagForPath(String path){
		if (path == null) return null;
		String locale = null;
		String[] pathComponents = path.split("/");
		if ("locales".equalsIgnoreCase(pathComponents[0])){
			if (pathComponents.length < 2) return null;
			return pathComponents[1];
		}
		return locale;
	}
	
	/**
	 * Return the set of valid default files for each locale in the zip
	 * @param zip
	 * @param locales
	 * @param defaults
	 * @return
	 */
	public static String[] getDefaults(ZipFile zip, String[] locales, String[] defaults){
		ArrayList<String> content = new ArrayList<String>();
		for (String start: defaults){
			try {
				String[] paths = locateFilePaths(start, locales, zip);
				if (paths != null){
					for (String path:paths) content.add(path);
				}
			} catch (Exception e) {
				// ignore and move onto next
			}
		}
		return (String[]) content.toArray(new String[content.size()]);	
	}
	
	public static File createUnpackedWidgetFolder(String widgetFolder, String folder) throws IOException{
		folder = convertIdToFolderName(folder);
		String serverPath = widgetFolder + File.separator + folder;
		File file = new File(convertPathToPlatform(serverPath));
		return file;
	}

	public static String getURLForWidget(String widgetFolder, String folder, String file){
		folder = convertIdToFolderName(folder);
		String path = convertPathToRelativeUri(widgetFolder + File.separator + folder + File.separator + file); //$NON-NLS-1$ //$NON-NLS-2$
		return path;
	}
	
	public static String convertIdToFolderName(String folder){
		if(folder.startsWith("http://")){ //$NON-NLS-1$
			folder = folder.substring(7, folder.length());
		}
		folder.replaceAll(" ", ""); //$NON-NLS-1$ //$NON-NLS-2$
		return folder;
	}
	
	public static File dealWithDroppedFile(String uploadPath, File file) throws IOException{	
		String serverPath = convertPathToPlatform(uploadPath);
		File uFile = new File(serverPath + File.separator + file.getName());
		FileUtils.copyFile(file, uFile);
		file.delete();
		return uFile;
	}

	public static File dealWithUploadFile(String uploadPath, HttpServletRequest request) throws Exception {
		File uFile = null;
		String serverPath = convertPathToPlatform(uploadPath);
		_logger.debug(serverPath);
		String archiveFileName = null;
		if (FileUploadBase.isMultipartContent(request)) {
			_logger.debug("uploading file..."); //$NON-NLS-1$
			DiskFileUpload fu = new DiskFileUpload();
			// maximum size before a FileUploadException will be thrown
			fu.setSizeMax(1024 * 1024 * 1024);
			// maximum size that will be stored in memory
			fu.setSizeThreshold(1024 * 1024);
			// the location for saving data that is larger than
			// getSizeThreshold()
			fu.setRepositoryPath(uploadPath);

			List<?> fileItems = fu.parseRequest(request);
			if (!fileItems.isEmpty()) {
				Iterator<?> i = fileItems.iterator();
				FileItem fi = (FileItem) i.next();
				File file = new File(convertPathToPlatform(fi.getName()));
				archiveFileName = file.getName();

				uFile = new File(serverPath + File.separator + archiveFileName);

				fi.write(uFile);
				_logger.debug("Upload completed successfully" +  "[" //$NON-NLS-1$ //$NON-NLS-2$
						+ archiveFileName + "]-" //$NON-NLS-1$
						+ (fi.isInMemory() ? "M" : "D")); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return uFile;
	}


	public static String convertPathToRelativeUri(String path){
		return path.replace('\\', '/');
	}

	public static String convertPathToPlatform(String path) {
		String result = path.replace('\\', '/')
		.replace('/', File.separatorChar);
		if (result.endsWith(File.separator)) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	public static boolean removeWidgetResources(String WIDGETFOLDER, String widgetGuid){
		String folder = convertIdToFolderName(widgetGuid);
		String serverPath = WIDGETFOLDER + File.separator + folder;
		File pFolder = new File(convertPathToPlatform(serverPath));
		try {
			_logger.debug("Deleting folder:"+pFolder.getCanonicalFile().toString()); //$NON-NLS-1$
			if (pFolder.getParent() != null) // never call on a root folder
				FileUtils.deleteDirectory(pFolder);
		}
		catch (Exception ex) {
			_logger.error(ex);
		}
		return true;
	}
	
	public static W3CWidget processWidgetPackage(File zipFile, String localWidgetPath, String WIDGETFOLDER, String[] locales, IStartPageProcessor processor, String[] features) throws BadWidgetZipFileException, BadManifestException{
		ZipFile zip;
		try {
			zip = new ZipFile(zipFile);
		} catch (IOException e) {
			throw new BadWidgetZipFileException();
		}
		if (WidgetPackageUtils.hasManifest(zip)){
			try {
				// build the model
				WidgetManifestModel widgetModel = new WidgetManifestModel(WidgetPackageUtils.extractManifest(zip), locales, features, zip);															

				// get the widget identifier
				String manifestIdentifier = widgetModel.getIdentifier();						
				// create the folder structure to unzip the zip into
				File newWidgetFolder = WidgetPackageUtils.createUnpackedWidgetFolder(WIDGETFOLDER, manifestIdentifier);
				// now unzip it into that folder
				WidgetPackageUtils.unpackZip(zip, newWidgetFolder);	
				
				// Iterate over all start files and update paths
				for (IContentEntity content: widgetModel.getContentList()){
					// now update the js links in the start page
					File startFile = new File(newWidgetFolder.getCanonicalPath() + File.separator + content.getSrc());
					String relativestartUrl = (WidgetPackageUtils.getURLForWidget(localWidgetPath, manifestIdentifier, content.getSrc())); 					
					content.setSrc(relativestartUrl);
					if(startFile.exists()){		
						processor.processStartFile(startFile, widgetModel);
					}	
				}
				if (widgetModel.getContentList().isEmpty()){
					throw new InvalidStartFileException();
				}
				
				// get the path to the root of the unzipped folder
				String localPath = WidgetPackageUtils.getURLForWidget(localWidgetPath, manifestIdentifier, "");
				// now pass this to the model which will prepend the path to local resources (not web icons)
				widgetModel.updateIconPaths(localPath);				
				
				// check to see if this widget already exists in the DB - using the ID (guid) key from the manifest
				return widgetModel;
			} catch (InvalidStartFileException e) {
				throw e;
			} catch (BadManifestException e) {
				throw e;
			} catch (Exception e){
				throw new BadManifestException();
			}
		}
		else{
			// no manifest file found in zip archive
			throw new BadWidgetZipFileException(); //$NON-NLS-1$ 
		}
	}
	
	/**
	 * Checks for the existence of the Manifest.
	 * TODO not sure if this properly handles case-sensitive entries?
	 * @param zipfile
	 * @return true if the zip file has a manifest
	 */
	public static boolean hasManifest(ZipFile zipfile){
		return zipfile.getEntry(IW3CXMLConfiguration.MANIFEST_FILE)!=null;
	}

	/**
	 * Retrieves the Manifest entry as a String
	 * @param zipFile the zip file from which to extract the manifest
	 * @return a String representing the manifest contents
	 * @throws IOException
	 */
	public static String extractManifest(ZipFile zipFile) throws IOException{
		ZipArchiveEntry entry = zipFile.getEntry(IW3CXMLConfiguration.MANIFEST_FILE);
		return IOUtils.toString(zipFile.getInputStream(entry));
	}

	/**
	 * uses apache commons compress to unpack all the zip entries into a target folder
	 * partly adapted from the examples on the apache commons compress site, and an
	 * example of generic Zip unpacking. Note this iterates over the ZipArchiveEntry enumeration rather
	 * than use the more typical ZipInputStream parsing model, as according to the doco it will
	 * more reliably read the entries correctly. More info here: http://commons.apache.org/compress/zip.html
	 * @param zipfile the Zip File to unpack
	 * @param targetFolder the folder into which to unpack the Zip file
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public static void unpackZip(ZipFile zipfile, File targetFolder) throws IOException {
		targetFolder.mkdirs();
		BufferedOutputStream out = null;
		InputStream in = null;
		ZipArchiveEntry zipEntry;

		Enumeration entries = zipfile.getEntries();
		try {
			while (entries.hasMoreElements()){
				zipEntry = (ZipArchiveEntry)entries.nextElement();
				// Don't add directories - use mkdirs instead
				if(!zipEntry.isDirectory()) {
					File outFile = new File(targetFolder, zipEntry.getName());

					// Ensure that the parent Folder exists
					if(!outFile.getParentFile().exists()) {
						outFile.getParentFile().mkdirs();
					}
					// Read the entry
					in = new BufferedInputStream(zipfile.getInputStream(zipEntry));
					out = new BufferedOutputStream(new FileOutputStream(outFile));
					IOUtils.copy(in, out);
					// Restore time stamp
					outFile.setLastModified(zipEntry.getTime());
					
					// Close File
					out.close();
					// Close Stream
					in.close();
				}
			}

		}
		// We'll catch this exception to close the file otherwise it remains locked
		catch(IOException ex) {
			if (in != null){
				in.close();
			}
			if(out != null) {
				out.flush();
				out.close();
			}
			// And throw it again
			throw ex;
		}
	}
}