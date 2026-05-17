local metadata =
{
    plugin =
    {
        format = "jar",
        manifest =
        {
            permissions = {},
            usesPermissions = {},
            usesFeatures = {},
            applicationChildElements =
            {
                -- FileProvider needed to share attachment files with mail apps via content:// URIs.
                [[
                <provider
                    android:name="androidx.core.content.FileProvider"
                    android:authorities="${applicationId}.fileprovider"
                    android:exported="false"
                    android:grantUriPermissions="true">
                    <meta-data
                        android:name="android.support.FILE_PROVIDER_PATHS"
                        android:resource="@xml/file_paths" />
                </provider>
                ]],
            },
            manifestChildElements =
            {
                -- Android 11+ package visibility: without this <queries> block,
                -- queryIntentActivities(mailto:) returns empty and the plugin
                -- cannot enumerate mail apps to build the mail-only chooser.
                [[
                <queries>
                    <intent>
                        <action android:name="android.intent.action.SENDTO" />
                        <data android:scheme="mailto" />
                    </intent>
                </queries>
                ]],
            },
        },
    },
}

return metadata
