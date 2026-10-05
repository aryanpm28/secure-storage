# SecureStorage

Portfolio project that demonstrates a **private online file storage system** using JWT authentication, file ownership checks, secure uploads, and a custom Spring Security filter.

SecureStorage supports **images, videos, and documents** under one account. Every file operation is authenticated and checked against the user who owns the file.

## Live Demo

https://secure-storage-nine.vercel.app/

## Features

- User registration and login with JWT authentication
- Upload images, videos, and documents
- View and preview owned files
- Delete owned files
- Protected raw file-serving endpoint
- Custom Spring Security `OncePerRequestFilter`
- JWT validation
- Blocked IP/user checks
- Access-attempt logging
- Automatic temporary blocking after repeated failures
- BCrypt password hashing
- Path-traversal protection
- File-size restrictions
- Content-Type and magic-byte validation
- Ownership checks on file listing, preview, download, and deletion

A renamed `.exe` claiming to be a JPEG is rejected because the application does not trust the browser's declared Content-Type alone.

## Supported Files

| Category | Formats              | Max size |
| -------- | -------------------- | -------- |
| Image    | JPEG, PNG, GIF, WebP | 5 MB     |
| Video    | MP4, WebM, MOV       | 100 MB   |
| Document | PDF, TXT, DOC, DOCX  | 20 MB    |

Every upload is checked using both the declared Content-Type and the file's actual magic bytes.

> **DOC/DOCX limitation:** `.docx` is a ZIP-based format and `.doc` uses OLE. They do not have a simple magic-byte signature like JPEG or PDF. These files are therefore checked for the expected container format before falling back to the declared Content-Type. A production system could use deeper document inspection or a dedicated content-sniffing library.

## How it works

1. **Authentication** — the user registers or logs in and receives a JWT
2. **File upload** — the authenticated user uploads an image, video, or document
3. **Validation** — the backend checks file size, Content-Type, magic bytes, and filename safety
4. **Ownership** — the uploaded file is associated with the authenticated user
5. **Storage** — the file is stored securely and linked to its database record
6. **File access** — preview, download, listing, and deletion are allowed only for the file owner

## Tech Stack

| Layer    | Technology                                   |
| -------- | -------------------------------------------- |
| Backend  | Java 21, Spring Boot 3, Spring Security, JWT |
| Database | MySQL, Spring Data JPA                        |
| Security | JWT, BCrypt, OncePerRequestFilter             |
| Build    | Maven, Lombok                                |
| Frontend | React, Vite, Axios, Plain CSS                 |
| Storage  | Local filesystem                              |
| Hosting  | Vercel                                       |

## Architecture

    User
     ↓
    React + Vite
     ↓
    Spring Boot REST API
     ↓
    Spring Security
     ├── JWT authentication
     ├── IP/user blocking
     └── Access logging
     ↓
    File validation
     ├── Content-Type check
     ├── Magic-byte check
     ├── Size check
     └── Path-traversal protection
     ↓
    MySQL
     ↓
    File storage
     ↓
    Owned file preview/download/delete

## Project Structure

    secure-storage/
    ├── backend/
    │   ├── src/
    │   │   └── main/
    │   │       ├── java/
    │   │       │   └── com/securestorage/
    │   │       │       ├── controller/
    │   │       │       ├── service/
    │   │       │       ├── repository/
    │   │       │       ├── entity/
    │   │       │       ├── security/
    │   │       │       ├── filter/
    │   │       │       ├── exception/
    │   │       │       ├── config/
    │   │       │       └── dto/
    │   │       └── resources/
    │   ├── uploads/
    │   └── ...
    │
    ├── frontend/
    │   ├── src/
    │   │   ├── pages/
    │   │   ├── services/
    │   │   ├── components/
    │   │   └── App.jsx
    │   ├── public/
    │   └── ...
    │
    └── docs/

## Setup

### Backend

Make sure MySQL is running and configure the required environment variables.

    cd backend
    mvn spring-boot:run

Backend:

    http://localhost:8080

### Frontend

    cd frontend
    npm install
    npm run dev

Frontend:

    http://localhost:5173

## Environment Variables

### Backend

| Variable        | Purpose                          |
| --------------- | -------------------------------- |
| `DB_PASSWORD`   | MySQL password                   |
| `JWT_SECRET`    | JWT signing secret               |
| `DB_URL`        | MySQL database connection URL   |
| `DB_USERNAME`   | MySQL username                   |

Additional Spring Security blocking configuration can be set in `application.properties`.

### Security Configuration

    security.block.threshold=5
    security.block.duration-minutes=30

By default, repeated failed authentication/access attempts can result in a temporary block.

> Never commit real database passwords or JWT secrets to the repository.

## API

| Method | Endpoint               | Auth | Description                  |
| ------ | ---------------------- | ---- | ---------------------------- |
| POST   | `/register`            | No   | Create account               |
| POST   | `/login`               | No   | Login and receive JWT        |
| POST   | `/files/upload`        | Yes  | Upload a file                |
| GET    | `/files`               | Yes  | List files owned by user     |
| DELETE | `/files/{id}`          | Yes  | Delete an owned file         |
| GET    | `/files/file/{name}`   | Yes  | Serve an owned file          |

Protected requests use:

    Authorization: Bearer <JWT>

## File Ownership

Users can only access files belonging to their own account.

Ownership is checked for:

- File listing
- File preview
- Raw file access
- File deletion

The raw file endpoint:

    GET /files/file/{name}

is **not public**. The requester must be authenticated and must own the requested file.

## Security Filter

The main security feature is a custom Spring Security `OncePerRequestFilter`.

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

The filter is responsible for enforcing authentication and additional access-control checks before protected requests reach the application.

## File Security

### Path Traversal Protection

File names are validated before being resolved on disk.

Attempts such as:

    ../file.txt
    ../../file.txt

are rejected.

### Content-Type Validation

The application checks both:

- Declared MIME type
- Actual file bytes

For example:

    malware.exe → photo.jpg

does not automatically make the file a valid JPEG.

### File Size Restrictions

    Image    → 5 MB
    Video    → 100 MB
    Document → 20 MB

### Magic-Byte Validation

The application checks the actual file signature where a reliable signature is available.

This helps prevent files from being accepted only because their filename was changed.

## Viewing Files

- **Images** — preview inline
- **Videos** — play inline
- **PDFs** — embedded viewer
- **TXT** — direct download
- **DOC/DOCX** — direct download

## Security Notes

- Passwords are hashed using BCrypt
- JWT protects authenticated requests
- File ownership is enforced server-side
- Raw file access requires authentication
- File deletion requires ownership
- Path traversal is blocked
- File sizes are restricted
- File content is validated
- Access attempts are logged
- Repeated failures can trigger temporary blocking
- Real secrets should be stored through environment variables

## Error Handling

The backend uses centralized exception handling for application and security-related errors.

The API returns appropriate error responses for invalid requests, unauthorized access, invalid files, and other application failures instead of exposing internal implementation details.

## Deployment

    Vercel
       ↓
    React + Vite frontend
       ↓
    Spring Boot backend
       ↓
    MySQL
       ↓
    Secure file storage

### Production Frontend

https://secure-storage-nine.vercel.app/

## Production Status

The deployed application provides:

- React frontend
- Spring Boot backend
- JWT authentication
- Secure file uploads
- File ownership checks
- File preview and download
- File deletion
- MySQL persistence
- Spring Security filtering
- File validation
- Temporary IP/user blocking

## Limitations

- Current storage uses the local filesystem
- A production cloud-storage system could use object storage instead
- DOC/DOCX validation is less precise than formats with standard magic-byte signatures
- Temporary blocks and other runtime state depend on the running backend instance
- Production systems could additionally use malware scanning, encryption at rest, object storage, and distributed rate limiting

## Portfolio Highlights

This project demonstrates:

- React frontend development
- Vite
- Java 21
- Spring Boot
- Spring Security
- JWT authentication
- BCrypt password hashing
- REST API development
- Spring Data JPA
- MySQL
- Custom `OncePerRequestFilter`
- File ownership and authorization
- Secure file uploads
- Magic-byte validation
- Content-Type validation
- Path-traversal protection
- File-size restrictions
- Access-attempt logging
- Temporary IP/user blocking
- Vercel deployment

## Author

**Aryan Patil**

GitHub:

https://github.com/aryanpm28

Live Demo:

https://secure-storage-nine.vercel.app/
