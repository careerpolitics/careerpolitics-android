#!/usr/bin/env bash
set -euo pipefail

TARGET_FILE="app/google-services.json"
mkdir -p "$(dirname "$TARGET_FILE")"

if [[ -n "${GOOGLE_SERVICES_JSON_BASE64:-}" ]]; then
  echo "$GOOGLE_SERVICES_JSON_BASE64" | base64 --decode > "$TARGET_FILE"
  echo "Using google-services.json from CI secret."
  exit 0
fi

cat > "$TARGET_FILE" <<'JSON'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "ci-placeholder",
    "storage_bucket": "ci-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:placeholderrelease",
        "android_client_info": {
          "package_name": "com.murari.careerpolitics"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "placeholder"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:placeholderdebug",
        "android_client_info": {
          "package_name": "com.murari.careerpolitics.debug"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "placeholder"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:placeholderstaging",
        "android_client_info": {
          "package_name": "com.murari.careerpolitics.staging"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "placeholder"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
JSON

echo "GOOGLE_SERVICES_JSON_BASE64 not set; using CI placeholder google-services.json for release/debug/staging packages."
