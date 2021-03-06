safebackup
==========

OI Safe Backup

A simple application to assist with backing up the oisafe.xml created
by OI Safe.  You must first use OI Safe to Menu -> Backup to generate
the oisafe.xml backup file.  Then you can use OI Safe Backup to send
the file up to Dropbox.  You can also get oisafe.xml from Dropbox for
easy Restore into OI Safe.

The Dropbox Core API Android SDK must be downloaded and unzipped directly
in the OISafeBackup folder.

https://www.dropbox.com/developers/core/sdks/android

After unziping the dropbox-android-sdk, use Gradle to copy the appropriate
libraries with './gradlew copyDropboxToLib'.

You will also need to setup a key at the App Console.

https://www.dropbox.com/developers/apps

With the key and secret from the App Console, you then need to copy 
app/src/main/java/org/openintents/oisafebackup/dropbox/KeySecret-template.java.txt 
to KeySecret.java and update the APP_KEY and APP_SECRET accordingly.

```java
final static public String APP_KEY = "REPLACE_WITH_KEY";
final static public String APP_SECRET = "REPLACE_WITH_SECRET";
```

Also update the AndroidManifest.xml in the dropbox activity.

```xml
<activity
    android:name="com.dropbox.client2.android.AuthActivity"
    android:configChanges="orientation|keyboard"
    android:launchMode="singleTask" >
    <intent-filter>

	<!-- Change this to be db- followed by your app key -->
	<data android:scheme="db-makethisyourkey" />
```

