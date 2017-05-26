package com.universal.storage;

import com.universal.util.PathValidator;
import com.universal.error.UniversalIOException;
import com.universal.storage.settings.UniversalSettings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import java.security.GeneralSecurityException;
import com.google.api.client.http.FileContent;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.services.drive.Drive;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;

/**
 * This class is the implementation of a storage that will manage files within a Google Drive folder.
 * This implementation will manage file using a Google Drive folder as a root storage.
 */
public class UniversalGoogleDriveStorage extends UniversalStorage {
    private Drive service;
    private static GoogleCredential credential = null;
    private static final Object lock = new Object();

    /**
     * This constructor receives the settings for this new FileStorage instance.
     * 
     * @param settings for this new FileStorage instance.
     */
    public UniversalGoogleDriveStorage(UniversalSettings settings) {
        super(settings);
        initializeDrive();
    }

    /**
     * This method initializes the Drive object.
     */
    private void initializeDrive() {
        try {
            Credential credential = authorize();
            service = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(), 
                    JacksonFactory.getDefaultInstance(), 
                    credential).setApplicationName("Universal Storage").build();
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * This method builds a Credential instance for Google Drive service.
     */
    private Credential authorize() throws IOException, GeneralSecurityException {
        synchronized(lock) {
            if (credential == null || credential.getExpiresInSeconds() < 10) {
                GoogleCredential.Builder credentialBuilder = new GoogleCredential.Builder().
                    setTransport(GoogleNetHttpTransport.newTrustedTransport()).
                    setJsonFactory(JacksonFactory.getDefaultInstance()).
                    setClientSecrets(this.settings.getGoogleDriveClientId(), this.settings.getGoogleDriveClientSecret());

                credential = credentialBuilder.build();
                credential.setRefreshToken(this.settings.getGoogleDriveRefreshToken());
                credential.refreshToken();
            }

            return credential;
        }
    }

    /**
     * This method stores a file within the storage provider according to the current settings.
     * The method will replace the file if already exists within the root.
     * 
     * For exemple:
     * 
     * path == null
     * File = /var/wwww/html/index.html
     * Root = /storage/
     * Copied File = /storage/index.html
     * 
     * path == "myfolder"
     * File = /var/wwww/html/index.html
     * Root = /storage/
     * Copied File = /storage/myfolder/index.html
     * 
     * If this file is a folder, a error will be thrown informing that should call the createFolder method.
     * 
     * Validations:
     * Validates if root is a bucket.
     * 
     * @param file to be stored within the storage.
     * @param path is the path for this new file within the root.
     * @throws UniversalIOException when a specific IO error occurs.
     */
    void storeFile(File file, String path) throws UniversalIOException {
        if (file.isDirectory()) {
            throw new UniversalIOException(file.getName() + " is a folder.  You should call the createFolder method.");
        }

        if (path == null) {
            path = "";
        }
        
        try {
            List<com.google.api.services.drive.model.File> files = service.files().list().setQ("name = '" + this.settings.getRoot() + 
                        "' and trashed = false").execute().getFiles();

            if (files.isEmpty()) {
                throw new UniversalIOException(this.settings.getRoot() + " doesn't exist as a root storage.");
            }

            String rootId = files.get(0).getId();
            if (!path.trim().equals("")) {
                String [] subFolders = path.trim().split("/");
                rootId = discoverPath(subFolders, 0, rootId);
            }

            deleteFiles(rootId, file.getName());

            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(file.getName());
            fileMetadata.setParents(Arrays.asList(rootId));

            FileContent mediaContent = new FileContent("", file);

            Drive.Files.Create insert = service.files().create(fileMetadata, mediaContent);
            MediaHttpUploader uploader = insert.getMediaHttpUploader();
            uploader.setDirectUploadEnabled(true);
            
            insert.execute();
        } catch (Exception e) {
            throw new UniversalIOException(e.getMessage());
        }
    }

    private String discoverPath(String [] subFolders, int index, String currentParentId) 
                    throws IOException {
        return discoverPath(subFolders, index, currentParentId, true);
    }

    /**
     * This method will discover the sobfolders according to the path.
     * 
     * @param subFolders in context according to the path.
     * @param index current index.
     * @param currentParentId is the folderId according to the current loop.
     * @param createFolder while this process is descovering the subfolders the creation of a folder will be executed
     *        if one of then doesn't exist.
     */
    private String discoverPath(String [] subFolders, int index, String currentParentId, boolean createFolder) 
                    throws IOException {
        String sf = subFolders[index];
        
        List<com.google.api.services.drive.model.File> currentFolders = service.files().
                list().setQ("'" + currentParentId + "' in parents and name = '" + sf + 
                            "' and mimeType = 'application/vnd.google-apps.folder' and trashed = false").execute().getFiles();
        if (currentFolders.size() == 0) {
            if (createFolder) {
                /**
                 * Create sub folder.
                 */
                com.google.api.services.drive.model.File newFolder = new com.google.api.services.drive.model.File();
                newFolder.setName(sf);
                newFolder.setMimeType("application/vnd.google-apps.folder");
                newFolder.setParents(Arrays.asList(currentParentId));
                com.google.api.services.drive.model.File nf = service.files().create(newFolder).execute();
                currentParentId = nf.getId();
            } else {
                return null;
            }
        } else {
            /**
             * The current parent folder should have unique names.  So, we are going to get the 
             * first elementin within the list. 
             */
            currentParentId = currentFolders.get(0).getId();
        }

        if (index + 1 == subFolders.length) {
            return currentParentId;
        }

        return discoverPath(subFolders, ++index, currentParentId);
    }

    /**
     * This method deletes a list of files using the google batch process.
     * 
     * @param rootId is the parent folder id.
     * @param fileName is the file name target.
     */
    private void deleteFiles(String rootId, String fileName) throws IOException {
        List<com.google.api.services.drive.model.File> existsResult = service.files().
                        list().setQ("'" + rootId + "' in parents and name = '" + fileName + "' and trashed = false").
                        execute().getFiles();

        if (existsResult.size() > 0) {
            /**
             * Delete existing file.
             */
            for (com.google.api.services.drive.model.File f : existsResult) {
                service.files().delete(f.getId()).execute();
            }
        }
    }

    /**
     * This method stores a file according to the provided path within the storage provider 
     * according to the current settings.
     * 
     * @param path pointing to the file which will be stored within the storage.
     * @throws UniversalIOException when a specific IO error occurs.
     */
    void storeFile(String path) throws UniversalIOException {
        this.storeFile(new File(path), null);
    }

    /**
     * This method stores a file according to the provided path within the storage provider according to the current settings.
     * 
     * @param path pointing to the file which will be stored within the storage.
     * @param targetPath is the path within the storage.
     * 
     * @throws UniversalIOException when a specific IO error occurs.
     */
    void storeFile(String path, String targetPath) throws UniversalIOException {
        PathValidator.validatePath(path);
        PathValidator.validatePath(targetPath);

        this.storeFile(new File(path), targetPath);
    }

    /**
     * This method removes a file from the storage.  This method will use the path parameter 
     * to localte the file and remove it from the storage.  The deletion process will delete the last
     * version of this object.
     * 
     * Root = /gdstorage/
     * path = myfile.txt
     * Target = /gdstorage/myfile.txt
     * 
     * Root = /gdstorage/
     * path = myfolder/myfile.txt
     * Target = /gdstorage/myfolder/myfile.txt 
     * 
     * @param path is the object's path within the storage.  
     * @throws UniversalIOException when a specific IO error occurs.
     */
    void removeFile(String path) throws UniversalIOException {
        PathValidator.validatePath(path);
        
        int index = path.lastIndexOf("/");
        String fileName = path;
        if (index > -1) {
            fileName = path.substring(index + 1);
            path = path.substring(0, index);
        } else {
            path = "";
        }

        try {
            List<com.google.api.services.drive.model.File> files = service.files().list().setQ("name = '" + this.settings.getRoot() + 
                        "' and trashed = false").execute().getFiles();

            if (files.isEmpty()) {
                throw new UniversalIOException(this.settings.getRoot() + " doesn't exist as a root storage.");
            }

            String rootId = files.get(0).getId();
            if (!path.trim().equals("")) {
                String [] subFolders = path.trim().split("/");
                rootId = discoverPath(subFolders, 0, rootId);
            }

            deleteFiles(rootId, fileName);
        } catch (Exception e) {
            throw new UniversalIOException(e.getMessage());
        }      
    }

    /**
     * This method creates a new folder within the storage using the passed path. If the new folder name already
     * exists within the storage, this  process will skip the creation step.
     * 
     * Root = /gdstorage/
     * path = /myFolder
     * Target = /gdstorage/myFolder
     * 
     * Root = /gdstorage/
     * path = /folders/myFolder
     * Target = /gdstorage/folders/myFolder
     * 
     * @param path is the folder's path.
     * @param storeFiles is a flag to store the files after folder creation.
     * 
     * @throws UniversalIOException when a specific IO error occurs.
     * @throws IllegalArgumentException is path has an invalid value.
     */
    void createFolder(String path) throws UniversalIOException {
        PathValidator.validatePath(path);

        if ("".equals(path.trim())) {
            throw new UniversalIOException("Invalid path.  The path shouldn't be empty.");
        }

        try {
            List<com.google.api.services.drive.model.File> files = service.files().list().setQ("name = '" + this.settings.getRoot() + 
                        "' and trashed = false").execute().getFiles();

            if (files.isEmpty()) {
                throw new UniversalIOException(this.settings.getRoot() + " doesn't exist as a root storage.");
            }

            String rootId = files.get(0).getId();
            if (!path.trim().equals("")) {
                String [] subFolders = path.trim().split("/");
                discoverPath(subFolders, 0, rootId);
            }
        } catch (Exception e) {
            throw new UniversalIOException(e.getMessage());
        }   
    }

    /**
     * This method removes the folder located on that path.
     * The folder should be empty in order for removing.
     * 
     * Root = /storage/
     * path = myFolder
     * Target = /storage/myFolder
     * 
     * Root = /storage/
     * path = folders/myFolder
     * Target = /storage/folders/myFolder
     * 
     * @param path of the folder.
     */
    void removeFolder(String path) throws UniversalIOException {
        PathValidator.validatePath(path);

        if ("".equals(path.trim())) {
            return;
        }

        try {
            List<com.google.api.services.drive.model.File> files = service.files().list().setQ("name = '" + this.settings.getRoot() + 
                        "' and trashed = false").execute().getFiles();

            if (files.isEmpty()) {
                throw new UniversalIOException(this.settings.getRoot() + " doesn't exist as a root storage.");
            }

            String rootId = files.get(0).getId();
            if (!path.trim().equals("")) {
                String [] subFolders = path.trim().split("/");
                rootId = discoverPath(subFolders, 0, rootId, false);

                if (rootId == null) {
                    throw new UniversalIOException(path + " doesn't exist within storage.");
                } else {
                    service.files().delete(rootId).execute();
                }
            }
        } catch (Exception e) {
            throw new UniversalIOException(e.getMessage());
        }
    }

    /**
     * This method retrieves a file from the storage.
     * The method will retrieve the file according to the passed path.  
     * A file will be stored within the settings' tmp folder.
     * 
     * @param path in context.
     * @returns a file pointing to the retrieved file.
     */
    public File retrieveFile(String path) throws UniversalIOException {
        PathValidator.validatePath(path);

        if ("".equals(path.trim())) {
            return null;
        }

        if (path.trim().endsWith("/")) {
            throw new UniversalIOException("Invalid path.  Looks like you're trying to retrieve a folder.");
        }

        int index = path.lastIndexOf("/");
        String fileName = path;
        if (index > -1) {
            fileName = path.substring(index + 1);
        }

        InputStream stream = retrieveFileAsStream(path);
        File retrievedFile = new File(this.settings.getTmp(), fileName);

        try {
            FileUtils.copyInputStreamToFile(stream, retrievedFile);
        } catch (Exception e) {
            throw new UniversalIOException(e.getMessage());
        } finally {
            try {
                stream.close();
            } catch (Exception ignore) {}
        }

        return retrievedFile;
    }

    /**
     * This method retrieves a file from the storage as InputStream.
     * The method will retrieve the file according to the passed path.  
     * A file will be stored within the settings' tmp folder.
     * 
     * @param path in context.
     * @returns an InputStream pointing to the retrieved file.
     */
    public InputStream retrieveFileAsStream(String path) throws UniversalIOException {
        PathValidator.validatePath(path);

        if ("".equals(path.trim())) {
            return null;
        }

        if (path.trim().endsWith("/")) {
            throw new UniversalIOException("Invalid path.  Looks like you're trying to retrieve a folder.");
        }

        int index = path.lastIndexOf("/");
        String fileName = path;
        if (index > -1) {
            fileName = path.substring(index + 1);
            path = path.substring(0, index);
        } else {
            path = "";
        }

        try {
            List<com.google.api.services.drive.model.File> files = service.files().list().setQ("name = '" + this.settings.getRoot() + 
                        "' and trashed = false").execute().getFiles();

            if (files.isEmpty()) {
                throw new UniversalIOException(this.settings.getRoot() + " doesn't exist as a root storage.");
            }

            String rootId = files.get(0).getId();
            if (!path.trim().equals("")) {
                String [] subFolders = path.trim().split("/");
                rootId = discoverPath(subFolders, 0, rootId, false);
            }

            if (rootId == null) {
                throw new UniversalIOException(path + " doesn't exist within storage.");
            } else {
                files = service.files().list().setQ("'" + rootId + "' in parents and name = '" + fileName + 
                            "' and mimeType != 'application/vnd.google-apps.folder' and trashed = false").
                                execute().getFiles();
                if (files.size() == 0) {
                    throw new UniversalIOException(path + " doesn't exist within storage.");
                } else {
                    FileOutputStream outputStream = null;
                    try {
                        com.google.api.services.drive.model.File file = files.get(0);
                        outputStream = new FileOutputStream(new File(this.settings.getTmp(), file.getName()));
                        service.files().get(file.getId()).executeMediaAndDownloadTo(outputStream);

                        return new FileInputStream(new File(this.settings.getTmp(), file.getName()));
                    } finally {
                        try {
                            outputStream.close();
                        } catch (Exception ignore) {}                        
                    }
                }
            }
        } catch (Exception e) {
            throw new UniversalIOException(e.getMessage());
        }     
    }

    /**
     * This method cleans the context of this storage.  This method doesn't remove any file from the storage.
     * The method will clean the tmp folder to release disk usage.
     */
    public void clean() throws UniversalIOException  {
        try {
            FileUtils.cleanDirectory(new File(this.settings.getTmp()));
        } catch (Exception e) {
            throw new UniversalIOException(e.getMessage());
        }
    }
}