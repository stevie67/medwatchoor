<?php
/**
 * MedWatchoor Medication Data Upload Script
 * Receives JSON data from the watch app and saves it to stevie.json
 */

header('Content-Type: application/json');

// Basic security - you can change this token
define('UPLOAD_TOKEN', 'UeBSqVYo3I1huHzk6ABaoyozimTaASyBXiCLLe6Y');

// Target file
define('TARGET_FILE', __DIR__ . '/stevie.json');

// Check if this is a POST request
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Method not allowed']);
    exit;
}

// Check authorization token
$authHeader = isset($_SERVER['HTTP_AUTHORIZATION']) ? $_SERVER['HTTP_AUTHORIZATION'] : '';

// Log for debugging (remove this after testing)
error_log("Received auth header: " . $authHeader);
error_log("Expected: Bearer " . UPLOAD_TOKEN);

if ($authHeader !== 'Bearer ' . UPLOAD_TOKEN) {
    http_response_code(401);
    echo json_encode([
        'error' => 'Unauthorized',
        'debug' => 'Auth header mismatch'
    ]);
    exit;
}

// Get the raw POST data
$jsonData = file_get_contents('php://input');

if (empty($jsonData)) {
    http_response_code(400);
    echo json_encode(['error' => 'No data provided']);
    exit;
}

// Validate JSON
$data = json_decode($jsonData, true);
if (json_last_error() !== JSON_ERROR_NONE) {
    http_response_code(400);
    echo json_encode(['error' => 'Invalid JSON: ' . json_last_error_msg()]);
    exit;
}

// Validate structure
if (!isset($data['medications']) || !is_array($data['medications'])) {
    http_response_code(400);
    echo json_encode(['error' => 'Invalid data structure: missing medications array']);
    exit;
}

// Validate each medication
foreach ($data['medications'] as $medication) {
    if (!isset($medication['id']) || !isset($medication['name']) || !isset($medication['timeToTake'])) {
        http_response_code(400);
        echo json_encode(['error' => 'Invalid medication data: missing required fields']);
        exit;
    }
}

// Create backup of existing file
if (file_exists(TARGET_FILE)) {
    $backupFile = TARGET_FILE . '.backup-' . date('Y-m-d-H-i-s');
    copy(TARGET_FILE, $backupFile);

    // Keep only the last 5 backups
    $backups = glob(__DIR__ . '/stevie.json.backup-*');
    if (count($backups) > 5) {
        usort($backups, function($a, $b) {
            return filemtime($a) - filemtime($b);
        });
        for ($i = 0; $i < count($backups) - 5; $i++) {
            unlink($backups[$i]);
        }
    }
}

// Pretty print the JSON for readability
$prettyJson = json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);

// Save to file
if (file_put_contents(TARGET_FILE, $prettyJson) === false) {
    http_response_code(500);
    echo json_encode(['error' => 'Failed to save file']);
    exit;
}

// Success
http_response_code(200);
echo json_encode([
    'success' => true,
    'message' => 'Data saved successfully',
    'timestamp' => date('c')
]);
