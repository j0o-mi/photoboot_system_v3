package com.boothyeah.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.util.store.FileDataStoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Handles Google Drive integration using OAuth 2.0:
 * 1. Authenticates with OAuth credentials JSON
 * 2. Uploads the final collage image to a specified folder
 * 3. Sets sharing to "anyone with link can view"
 * 4. Returns the public webViewLink for QR code generation
 *
 * SETUP REQUIRED:
 * - Create a Google Cloud project and enable Drive API
 * - Configure OAuth Consent Screen (Desktop App)
 * - Create OAuth 2.0 Client ID credentials and download the JSON
 * - Place the credentials JSON at configured AppSettings path
 * - App will open a browser for first-time login and save tokens locally
 */
public class GoogleDriveService {
    private static final Logger log = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String APP_NAME = "BoothYeah Photobooth";

    private Drive driveService;
    private boolean initialized = false;
    /** Stores the last error message for UI display */
    private String lastError = "";

    /**
     * Initialize the Drive service with OAuth credentials.
     *
     * @param credentialPath path to the oauth_credentials.json file
     */
    public void initialize(String credentialPath) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(credentialPath);
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), new InputStreamReader(in));

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientSecrets,
                    Collections.singletonList(DriveScopes.DRIVE))
                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("data/tokens")))
                    .setAccessType("offline")
                    .build();

            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

            HttpRequestInitializer requestInitializer = credential;

            // 2. Build the Drive API client
            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    requestInitializer)
                    .setApplicationName(APP_NAME)
                    .build();

            initialized = true;
            log.info("Google Drive service initialized successfully with OAuth");

        } catch (Exception e) {
            lastError = "OAuth Init failed: " + e.getMessage();
            log.error("Failed to initialize Google Drive service", e);
            initialized = false;
        }
    }

    /**
     * Upload an image file to the specified Google Drive folder.
     * Sets the file to be publicly viewable via link.
     *
     * @param localFile the image file to upload
     * @param folderId  the Google Drive folder ID to upload into
     * @return the public webViewLink, or null on failure
     */
    public String uploadImage(java.io.File localFile, String folderId) {
        if (!initialized || driveService == null) {
            log.error("Drive service not initialized — skipping upload");
            return null;
        }

        try {
            // 1. Create file metadata with parent folder
            File fileMetadata = new File();
            fileMetadata.setName(localFile.getName());
            if (folderId != null && !folderId.isEmpty()) {
                fileMetadata.setParents(Collections.singletonList(folderId));
            }

            // 2. Upload the file
            FileContent mediaContent = new FileContent("image/png", localFile);
            File uploaded = driveService.files().create(fileMetadata, mediaContent)
                    .setSupportsAllDrives(true)
                    .setFields("id, webViewLink, webContentLink")
                    .execute();

            log.info("File uploaded to Drive: id={}", uploaded.getId());

            // 3. Set sharing permission — anyone with link can view
            Permission permission = new Permission()
                    .setType("anyone")
                    .setRole("reader");
            driveService.permissions().create(uploaded.getId(), permission).execute();

            log.info("Sharing permission set. Link: {}", uploaded.getWebViewLink());
            return uploaded.getWebViewLink();

        } catch (IOException e) {
            log.error("Failed to upload to Google Drive", e);
            return null;
        }
    }

    /**
     * Test the Drive connection by listing files in the target folder.
     *
     * @param folderId the folder to test access on
     * @return true if accessible
     */
    public boolean testConnection(String folderId) {
        if (!initialized || driveService == null) {
            lastError = "Service not initialized";
            return false;
        }
        try {
            driveService.files().list()
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setQ("'" + folderId + "' in parents")
                    .setPageSize(1)
                    .execute();
            lastError = "";
            return true;
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            // Extract specific error reason for actionable feedback
            String reason = e.getDetails() != null ? e.getDetails().getMessage() : e.getMessage();
            if (reason != null && reason.contains("SERVICE_DISABLED")) {
                lastError = "Google Drive API is NOT enabled in your Cloud project.\n"
                        + "Go to: console.cloud.google.com → APIs → Enable Google Drive API";
            } else if (reason != null && reason.contains("notFound")) {
                lastError = "Folder not found. Check the folder ID and ensure it's shared with the service account.";
            } else {
                lastError = reason != null ? reason : e.getMessage();
            }
            log.error("Drive connection test failed: {}", lastError);
            return false;
        } catch (IOException e) {
            lastError = e.getMessage();
            log.error("Drive connection test failed", e);
            return false;
        }
    }

    /**
     * List recent files from a specific folder.
     *
     * @param folderId folder to query
     * @param maxResults max number of files to return
     * @return list of files, or empty list on failure
     */
    public java.util.List<File> listFiles(String folderId, int maxResults) {
        if (!initialized || driveService == null) return Collections.emptyList();
        try {
            return driveService.files().list()
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setQ("'" + folderId + "' in parents and trashed = false")
                    .setFields("files(id, name, thumbnailLink, createdTime)")
                    .setOrderBy("createdTime desc")
                    .setPageSize(maxResults)
                    .execute()
                    .getFiles();
        } catch (IOException e) {
            log.error("Failed to list files from Drive", e);
            return Collections.emptyList();
        }
    }

    /**
     * Delete a specific file from Google Drive.
     *
     * @param fileId the ID of the file to delete
     * @return true if successful
     */
    public boolean deleteFile(String fileId) {
        if (!initialized || driveService == null) return false;
        try {
            driveService.files().delete(fileId).setSupportsAllDrives(true).execute();
            return true;
        } catch (IOException e) {
            log.error("Failed to delete file from Drive: {}", fileId, e);
            return false;
        }
    }

    public boolean isInitialized() { return initialized; }
    public String getLastError() { return lastError; }
}
