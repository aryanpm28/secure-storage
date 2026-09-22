# SecureStorage

Portfolio project that demonstrates a **private online file storage system** using JWT authentication, file ownership checks, secure uploads, and a custom Spring Security filter.

SecureStorage supports **images, videos, and documents** under one account. Every file operation is authenticated and checked against the user who owns the file.

## Features

* User registration and login with JWT authentication
* Upload images, videos, and documents
* View and preview owned files
* Delete owned files
* Protected raw file-serving endpoint
* Custom Spring Security `OncePerRequestFilter`
* JWT validation
* Blocked IP/user checks
* Access-attempt logging
* Automatic temporary blocking after repeated failures
* BCrypt password hashing
* Path-traversal protection
* File-size restrictions
* Content-Type and magic-byte validation
* Ownership checks on file listing, preview, download, and deletion

A renamed `.exe` claiming to be a JPEG is rejected because the application does not trust the browser's declared Content-Type alone.

## Supported Files

```text
| Category | Formats              | Max size |
| -------- | -------------------- | -------- |
| Image    | JPEG, PNG, GIF, WebP | 5 MB     |
| Video    | MP4, WebM, MOV       | 100 MB   |
| Document | PDF, TXT, DOC, DOCX  | 20 MB    |
```

Every upload is checked using both the declared Content-Type and the file's actual magic bytes.

> **DOC/DOCX limitation:** `.docx` is a ZIP-based format and `.doc` uses OLE. They do not have a simple magic-byte signature like JPEG or PDF. These files are therefore checked for the expected container format before falling back to the declared Content-Type. A production system could use deeper document inspection or a dedicated content-sniffing library.

## Tech Stack

```text
| Layer    | Technology                                   |
| -------- | -------------------------------------------- |
| Backend  | Java 21, Spring Boot 3, Spring Security, JWT |
| Database | MySQL, Spring Data JPA                        |
| Security | JWT, BCrypt, OncePerRequestFilter             |
| Build    | Maven, Lombok                                |
| Frontend | React, Vite, Axios, Plain CSS                 |
| Storage  | Local filesystem                              |
```

## Project Structure

```text
secure-storage
├── backend
│ ├── src/main/java/com/securestorage
│ │ ├── controller
│ │ ├── service
│ │ ├── repository
│ │ ├── entity
│ │ ├── security
│ │ ├── filter
│ │ ├── exception
│ │ ├── config
│ │ └── dto
│ └── uploads
│
└── frontend
  ├── src
  │ ├── pages
  │ ├── services
  │ └── App.jsx
  └── ...
```

## Setup

### 1. MySQL

Make sure MySQL is running.

Set real credentials using environment variables. Do not commit real passwords or JWT secrets to `application.properties`.

```bash
export DB_PASSWORD=your_real_mysql_password
export JWT_SECRET=$(openssl rand -hex 48)
```

For Windows PowerShell:

```powershell
$env:DB_PASSWORD="your_real_mysql_password"
$env:JWT_SECRET="your_generated_jwt_secret"
```

### 2. Run Backend

```bash
cd backend
mvn spring-boot:run
```

Backend:

```text
http://localhost:8080
```

### 3. Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend:

```text
http://localhost:5173
```

## API

```text
| Method | Endpoint             | Auth | Description                    |
| ------ | -------------------- | ---- | ------------------------------ |
| POST   | /register            | No   | Create account                 |
| POST   | /login               | No   | Login and receive JWT          |
| POST   | /files/upload        | Yes  | Upload a file                  |
| GET    | /files               | Yes  | List my files                  |
| DELETE | /files/{id}          | Yes  | Delete my file                 |
| GET    | /files/file/{name}   | Yes  | Serve an owned file            |
```

Protected requests use:

```http
Authorization: Bearer <JWT>
```

## File Ownership

Users can only access files belonging to their own account.

Ownership is checked for:

* File listing
* File preview
* Raw file access
* File deletion

The raw file endpoint:

```text
GET /files/file/{name}
```

is **not public**. The requester must be authenticated and must own the requested file.

## Security Filter

The main security feature is a custom Spring Security `OncePerRequestFilter`.

```text
Request
   ↓
Validate JWT
   ↓
Check blocked IP/user
   ↓
Log access attempt
   ↓
Allow or reject request
   ↓
Block after repeated failures
```

Configure the blocking behavior in `application.properties`:

```properties
security.block.threshold=5
security.block.duration-minutes=30
```

By default, 5 failed attempts can result in a 30-minute block.

## File Security

### Path Traversal

File names are validated before being resolved on disk. Attempts such as:

```text
../file.txt
../../file.txt
```

are rejected.

### Content-Type Validation

The application checks both the declared MIME type and the actual file bytes.

For example, renaming:

```text
malware.exe → photo.jpg
```

does not make the file a valid JPEG.

### Serving Files

When files are served, the application determines the Content-Type from the actual file content rather than blindly trusting the extension.

## Viewing Files

* **Images** — preview inline
* **Videos** — play inline
* **PDFs** — embedded viewer
* **TXT** — direct download
* **DOC/DOCX** — direct download

## Security Notes

* Passwords are hashed using BCrypt
* JWT protects authenticated requests
* File ownership is enforced server-side
* Raw file access requires authentication
* File deletion requires ownership
* Path traversal is blocked
* File sizes are restricted
* File content is validated
* Access attempts are logged
* Repeated failures can trigger temporary blocking

## Portfolio Scope

SecureStorage is intentionally a **portfolio-scale private storage application**, not a production cloud-storage platform.

The project demonstrates:

* Spring Security
* JWT authentication
* Custom request filtering
* Authorization and ownership checks
* Secure file uploads
* File validation
* REST APIs
* Spring Data JPA
* MySQL
* React integration

A production system could additionally use object storage, malware scanning, encrypted storage, deeper document inspection, and more advanced rate limiting.
