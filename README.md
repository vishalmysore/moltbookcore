# Moltbook Core Library

Core library for Moltbook integration providing posting and scheduling logic.

## Overview

`moltbook-core` is a standalone Spring Boot library that provides the core functionality for the Moltbook platform. This library can be used as a dependency in other projects that need Moltbook integration capabilities.

## Features

- Core posting and scheduling logic
- Integration with Tools4AI
- Spring Boot-based service layer
- Agent actions and policy management
- Activity tracking service

## Installation

### Maven

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.vishalmysore.moltbook</groupId>
    <artifactId>moltbook-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Local Installation

To install this library to your local Maven repository:

```bash
mvn clean install
```

## Building from Source

### Prerequisites

- Java 18 or higher
- Maven 3.6 or higher

### Build Steps

```bash
# Clone the repository
cd moltbookcore

# Build the project
mvn clean install

# Run tests
mvn test
```

## Project Structure

```
moltbookcore/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/github/vishalmysore/
│   │   │       ├── agent/actions/      # Agent action implementations
│   │   │       ├── service/            # Core services
│   │   │       └── model/              # Data models
│   │   └── resources/
│   └── test/
│       └── java/                        # Unit tests
└── pom.xml
```

## Dependencies

- **Spring Boot 3.2.0**: Core framework
- **Tools4AI 1.1.9.9**: AI integration library
- **OkHttp 4.12.0**: HTTP client
- **Gson**: JSON processing
- **Lombok**: Boilerplate code reduction

## Maven Coordinates

```xml
<groupId>io.github.vishalmysore.moltbook</groupId>
<artifactId>moltbook-core</artifactId>
<version>1.0.0</version>
```

## Usage

This library is designed to be used as a dependency in Spring Boot applications. Once included, the core services and configurations will be automatically available through Spring's component scanning.

### Example

```java
import io.github.vishalmysore.service.MoltbookService;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class MyService {
    
    @Autowired
    private MoltbookService moltbookService;
    
    // Use moltbookService methods
}
```

## Related Projects

- **moltbookjava**: The main Moltbook Agent application that uses this core library

## License

See LICENSE file for details.

## Contributing

This is part of the Moltbook project ecosystem. For issues and contributions, please refer to the main project repository.
