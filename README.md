# Universal Storage Java API
## Google Drive provider

[![Build Status](https://travis-ci.org/dynamicloud/universal_storage_java_gd_api.svg?branch=master)](https://travis-ci.org/dynamicloud/universal_storage_java_gd_api)
![Version](https://img.shields.io/badge/api-v1.0.0-brightgreen.svg)

Universal storage provides you an interface for storing files according to your needs. With this Universal Storage Java API, you will be able to develop programs in Java and use an interface for storing your files within a s3 bucket as storage.

<hr>

**This documentation has the following content:**

1. [Maven project](maven-project)
2. [Test API](#test-api)
3. [Settings](#settings)
4. [Explanation for setting keys](#explanation-for-setting-keys)
5. [Retrieve Google Drive keys](#retrieve-google-drive-keys)
6. [How to use](#how-to-use)

# Maven project
This API follows the Maven structure to ease its installation within your project.

# Test API
If you want to test the API, follow these steps:

1. Create a folder and copy its the absolute path.  This folder will be your storage root target.
2. Create a folder and copy its the absolute path.  This folder will be your tmp folder.
3. Open with a text editor the settings.json located on test/resources/settings.json
```json
{
	"provider": "google.drive",
	"root": "universalstorage",
	"tmp": "/home/tmp",
	"google_drive": {
		"client_id":"",
		"client_secret":"",
		"refresh_token":""
	}
}
```
4. Paste the absolute paths, the root's path and the tmp's path.
5. Save the settings.json file.

**Now execute the following command:**

`mvn clean test` 

# Settings
**These are the steps for setting up Universal Storage in your project:**
1. You must create a file called settings.json (can be any name) and paste the following. 
```json
{
	"provider": "google.drive",
	"root": "universalstorage",
	"tmp": "/home/tmp",
	"google_drive": {
		"client_id":"",
		"client_secret":"",
		"refresh_token":""
	}
}
```
2. The root and tmp keys are the main data to be filled, create two folders representing each one root and tmp.
3. Save the file settings.json
4. Add the maven dependency in your pom.xml file.
```xml
<dependency>
   <groupId>org.dynamicloud.api</groupId>
   <artifactId>universalstorage.googledrive</artifactId>
   <version>1.0.0</version>
</dependency>
```

# Explanation for setting keys
`client_id` key provided by Google

`client_secret` key provided by Google.

`refresh_token` This token is used to regenerate the access token.

# Retrieve Google Drive keys
In order to use Google Drive as a storage, you need a Google account and retrieve three specific keys (client_id, client_secret and refresh_token).  The following steps won't show how to create a google account.

1. Create a google project [here](https://console.developers.google.com/).
2. After project creation, create the credentials for this project.  This step will generate two keys (`client_id` and `client_secret`).  Click on **Create credentials** and then **OAuth client ID**

<img src="https://s3.amazonaws.com/shared-files-2017/google_drive_credentials.png">

3. Select as Application Type: **Web application**
4. Enter a name for these new credentials
5. Fill the field Authorized redirect URIs with: https://developers.google.com/oauthplayground
6. Press **create**

<img src="https://s3.amazonaws.com/shared-files-2017/google_drive_credentials_form.png">

7. At this point you have generated the `client_id` and `client_secret` keys.  Copy these keys and paste them into a text file, you are going to use them later.
8. The last step is for retrieving the `refresh_token`.  Click [here](https://developers.google.com/oauthplayground) to start the process.
9. In Settings (gear icon), set
   1. Oauth flow: server
   2. Access type: offline
   3. Use your own OAuth credentials: TICK
   4. Client Id and Client Secret: the keys that you've stored within a file.
10. Press on **close**

<img src="https://s3.amazonaws.com/shared-files-2017/google_drive_settings_oauth.png"> 

11. On **Select & authorize APIs** scroll down and open the dropdown **Drive API v3** then select **https://www.googleapis.com/auth/drive** and **https://www.googleapis.com/auth/drive.file**
12. Press **Authorize APIs**.  You will be prompted to choose your Google account and confirm access.
13. Press **Allow**

<img src="https://s3.amazonaws.com/shared-files-2017/google_drive_settings_authorize.png"> 

14. Press on **Exchange Authorization code for tokens**.  Now, copy the refresh token and paste it with the file where you've stored the `client_id` and `client_secret` keys.
15. At this point you have the three keys.

```json
{
	"provider": "google.drive",
	"root": "universalstorage"
}
```
4. Universal storage needs access to your bucket.  We recommend to create a IAM user, apply the necessary permissions and generate access keys, paste these access keys into the settings.json file.
```json
{
	"provider": "google.drive",
	"root": "universalstorage",
	"tmp": "/home/tmp",
	"google_drive": {
		"client_id":"",
		"client_secret":"",
		"refresh_token":""
	}
}
```

### This api will get the client_id, client_secret and refresh_token through either this file or environment variables `client_id`, `client_secret` and `refresh_token`

The root folder is the storage where the files will be stored.
The tmp folder is where temporary files will be stored.
  
# How to use
**Examples for Storing files:**

1. Passing the settings programmatically
```java
try {
      UniversalStorage us = UniversalStorage.Impl.
          getInstance(new UniversalSettings(new File("/home/test/resources/settings.json")));
      us.storeFile(new File("/home/test/resources/settings.json"), "myfolder/innerfolder");
      us.storeFile(new File("/home/test/resources/settings.json"));
      us.storeFile(new File("/home/test/resources/settings.json").getAbsolutePath(), "myfolder/innerfolder");
      us.storeFile(new File("/home/test/resources/settings.json").getAbsolutePath());
} catch (UniversalStorageException e) {
    fail(e.getMessage());
}
```
2. The settings could be passed through either jvm parameter or environment variable.
3. If you want to pass the settings.json path through jvm parameter, in your java command add the following parameter:
     `-Duniversal.storage.settings=/home/test/resources/settings.json`
4. If your want to pass the settings.json path through environment variable, add the following variable:
     `universal_storage_settings=/home/test/resources/settings.json`

```java
try {
      UniversalStorage us = UniversalStorage.Impl.getInstance();
      us.storeFile(new File("/home/test/resources/settings.json"), "myfolder/innerfolder");
      us.storeFile(new File("/home/test/resources/settings.json"));
      us.storeFile(new File("/home/test/resources/settings.json").getAbsolutePath(), "myfolder/innerfolder");
      us.storeFile(new File("/home/test/resources/settings.json").getAbsolutePath());
} catch (UniversalStorageException e) {
    fail(e.getMessage());
}
```

**Remove file:**
```java
try {
      UniversalStorage us = UniversalStorage.Impl.getInstance();
      us.removeFile("/home/test/resources/settings.json");
} catch (UniversalStorageException e) {
    e.printStackTrace();
}

```

**Create folder:**

```java
try {
      UniversalStorage us = UniversalStorage.Impl.getInstance();
      us.createFolder("/myNewFolder");
} catch (UniversalStorageException e) {
    e.printStackTrace();
}

```

**Remove folder:**
```java
try {
      UniversalStorage us = UniversalStorage.Impl.getInstance();
      us.removeFolder("/myNewFolder");
} catch (UniversalStorageException e) {
    e.printStackTrace();
}
```

**Retrieve file:**
```java
try {
      UniversalStorage us = UniversalStorage.Impl.getInstance();
      us.retrieveFile("myFolder/file.txt");
} catch (UniversalStorageException e) {
    e.printStackTrace();
}
```

**Retrieve file as InputStream:**

This inputstream will use a file that was stored into the tmp folder.
```java
try {
      UniversalStorage us = UniversalStorage.Impl.getInstance();
      InputSstream stream = us.retrieveFileAsStream("myFolder/file.txt");
} catch (UniversalStorageException e) {
    e.printStackTrace();
}
```

**Clean up tmp folder:**
```java
try {
      UniversalStorage us = UniversalStorage.Impl.getInstance();
      us.clean();
} catch (UniversalStorageException e) {
    e.printStackTrace();
}
```
