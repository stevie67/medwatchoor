# Gymoor Server Setup

## Files

- `upload.php` - PHP script to receive and save exercise data from the watch app
- `.htaccess` - Apache configuration to pass Authorization headers to PHP
- `stevie.json` - Your exercise data (create this file manually or it will be created on first upload)

## Installation

1. Upload both files to your web server at `https://www.radig.com/gymoor/`:
   - `upload.php`
   - `.htaccess`

2. Set a secure token in `upload.php`:
   ```php
   define('UPLOAD_TOKEN', 'your-secret-token-here');
   ```
   Choose a long, random string for security.

3. Make sure the directory is writable by the web server:
   ```bash
   chmod 755 /path/to/gymoor/
   chmod 644 /path/to/gymoor/stevie.json
   ```

4. Update the token in your Android app (we'll add this next)

## Endpoints

### GET /gymoor/stevie.json
Returns the current exercise data.

### POST /gymoor/upload.php
Uploads new exercise data.

**Headers:**
- `Authorization: Bearer your-secret-token-here`
- `Content-Type: application/json`

**Body:**
```json
{
  "exercises": [
    {
      "id": 1,
      "name": "Exercise Name",
      "weight": 50,
      "notes": "Optional notes"
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "message": "Data saved successfully",
  "timestamp": "2025-11-01T12:00:00+00:00"
}
```

## Security Features

- Token-based authentication
- JSON validation
- Automatic backups (keeps last 5)
- File permission checks

## Backups

Backups are automatically created with timestamps:
- `stevie.json.backup-2025-11-01-12-00-00`

Only the last 5 backups are kept to save space.
