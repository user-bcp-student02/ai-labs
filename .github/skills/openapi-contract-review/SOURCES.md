# Sources and References

All guidelines in the OpenAPI Specification Reviewer are based on publicly available standards and best practices.

## Primary Sources

### OpenAPI Specification
- **OpenAPI Specification 3.0.3**
  - URL: https://spec.openapis.org/oas/v3.0.3
  - Publisher: OpenAPI Initiative (Linux Foundation)
  - Description: Official specification for OpenAPI 3.0.x
  - License: Apache 2.0

- **OpenAPI Specification 3.1.0**
  - URL: https://spec.openapis.org/oas/v3.1.0
  - Publisher: OpenAPI Initiative (Linux Foundation)
  - Description: Latest OpenAPI specification (JSON Schema compatible)
  - License: Apache 2.0

### REST API Design

- **REST API Design Rulebook**
  - Author: Mark Massé
  - Publisher: O'Reilly Media
  - ISBN: 978-1449310509
  - Topics: RESTful resource naming, HTTP methods, URI design

- **RESTful Web APIs**
  - Authors: Leonard Richardson, Mike Amundsen, Sam Ruby
  - Publisher: O'Reilly Media
  - ISBN: 978-1449358068
  - Topics: REST principles, hypermedia, API design patterns

- **Web API Design: The Missing Link**
  - Author: Google Cloud (Apigee)
  - URL: https://cloud.google.com/files/apigee/apigee-web-api-design-the-missing-link-ebook.pdf
  - Topics: API design best practices, naming conventions

## Supporting References

### API Documentation

- **Swagger/OpenAPI Best Practices**
  - URL: https://swagger.io/resources/articles/best-practices-in-api-documentation/
  - Publisher: SmartBear (Swagger creators)
  - Topics: Documentation quality, examples, descriptions

- **OpenAPI Style Guide**
  - URL: https://stoplight.io/blog/openapi-style-guide
  - Publisher: Stoplight
  - Topics: Consistent API design, naming conventions

### Security

- **OWASP API Security Top 10**
  - URL: https://owasp.org/www-project-api-security/
  - Publisher: OWASP Foundation
  - Topics: API security risks, authentication, authorization

- **OAuth 2.0 Specification**
  - URL: https://oauth.net/2/
  - Publisher: IETF (RFC 6749)
  - Topics: OAuth flows, scopes, security schemes

### HTTP Standards

- **HTTP Status Codes (RFC 7231)**
  - URL: https://tools.ietf.org/html/rfc7231#section-6
  - Publisher: IETF
  - Topics: HTTP response codes, semantics

- **HTTP Methods (RFC 7231)**
  - URL: https://tools.ietf.org/html/rfc7231#section-4
  - Publisher: IETF
  - Topics: GET, POST, PUT, PATCH, DELETE semantics

### JSON Schema

- **JSON Schema Specification**
  - URL: https://json-schema.org/
  - Publisher: JSON Schema Team
  - Topics: Schema validation, data types, constraints
  - Note: OpenAPI 3.1 is fully compatible with JSON Schema

### YAML

- **YAML Specification 1.2**
  - URL: https://yaml.org/spec/1.2/spec.html
  - Publisher: YAML Language Development Team
  - Topics: YAML syntax, formatting, best practices

## Industry Standards and Patterns

### Microsoft REST API Guidelines
- URL: https://github.com/microsoft/api-guidelines
- Topics: Naming conventions, pagination, error handling
- License: CC-BY-4.0

### Google API Design Guide
- URL: https://cloud.google.com/apis/design
- Topics: Resource-oriented design, naming, custom methods
- License: CC-BY-4.0

### Zalando RESTful API Guidelines
- URL: https://opensource.zalando.com/restful-api-guidelines/
- Topics: API design, compatibility, deprecation
- License: CC-BY-SA-4.0

### PayPal API Design Guidelines
- URL: https://github.com/paypal/api-standards
- Topics: REST patterns, naming, versioning
- License: Apache 2.0

## Guideline Categories and Attribution

### Structure & Format (8 guidelines)
**Sources:**
- OpenAPI Specification 3.0.3 (required fields, format)
- YAML Specification 1.2 (syntax)

**Referenced Guidelines:**
- API-VERSION: OpenAPI Spec Section 4.7.1
- INFO-REQUIRED: OpenAPI Spec Section 4.7.2
- SERVERS-DEFINED: OpenAPI Spec Section 4.7.5
- PATHS-REQUIRED: OpenAPI Spec Section 4.7.8
- COMPONENTS-REUSE: OpenAPI Spec Section 4.7.10
- TAGS-ORGANIZED: OpenAPI Spec Section 4.7.3
- EXTERNAL-DOCS: OpenAPI Spec Section 4.7.4
- VALID-YAML: YAML Specification

### Path & Operations (10 guidelines)
**Sources:**
- REST API Design Rulebook (naming, methods)
- OpenAPI Specification (operation object)
- RFC 7231 (HTTP methods)

**Referenced Guidelines:**
- PATH-NAMING: REST API Design Rulebook, Chapter 2
- PATH-PARAMETERS: OpenAPI Spec Section 4.7.9
- OPERATION-ID: OpenAPI Spec Section 4.7.13
- HTTP-METHODS: RFC 7231 Section 4.3

### Parameters (6 guidelines)
**Sources:**
- OpenAPI Specification (parameter object)
- Microsoft REST API Guidelines (naming)

**Referenced Guidelines:**
- PARAM-REQUIRED: OpenAPI Spec Section 4.7.9
- PARAM-DESC: Best practices from Swagger/OpenAPI documentation
- PARAM-VALIDATION: JSON Schema validation rules

### Schemas & Data Models (8 guidelines)
**Sources:**
- OpenAPI Specification (schema object)
- JSON Schema Specification
- Microsoft REST API Guidelines (models)

**Referenced Guidelines:**
- SCHEMA-REQUIRED: OpenAPI Spec Section 4.7.18
- SCHEMA-TYPES: JSON Schema data types
- SCHEMA-VALIDATION: JSON Schema validation keywords
- DISCRIMINATOR: OpenAPI Spec Section 4.7.20

### Responses (6 guidelines)
**Sources:**
- OpenAPI Specification (responses object)
- RFC 7231 (HTTP status codes)
- Google API Design Guide (errors)

**Referenced Guidelines:**
- RESPONSE-REQUIRED: OpenAPI Spec Section 4.7.16
- ERROR-RESPONSES: OWASP API Security, Google API Design Guide
- RESPONSE-CODES: RFC 7231 Section 6

### Security (6 guidelines)
**Sources:**
- OpenAPI Specification (security)
- OAuth 2.0 Specification (RFC 6749)
- OWASP API Security Top 10

**Referenced Guidelines:**
- SECURITY-SCHEMES: OpenAPI Spec Section 4.7.26
- OAUTH-SCOPES: RFC 6749 Section 3.3
- AUTH-ERRORS: OWASP API Security A2

### Documentation (6 guidelines)
**Sources:**
- Swagger/OpenAPI Best Practices
- Write the Docs (documentation standards)
- Microsoft REST API Guidelines

**Referenced Guidelines:**
- DESC-COMPLETE: Swagger documentation best practices
- MARKDOWN-FORMAT: OpenAPI Spec supports CommonMark
- EXAMPLES-PROVIDED: Best practices from API design guides

## Validation Tools

The following tools implement these standards and can validate OpenAPI specs:

- **Swagger Editor** - https://editor.swagger.io/
- **Spectral** - https://stoplight.io/open-source/spectral
- **OpenAPI Generator** - https://openapi-generator.tech/
- **Redoc** - https://github.com/Redocly/redoc
- **Swagger UI** - https://swagger.io/tools/swagger-ui/

## Additional Reading

### Books
- "API Design Patterns" by JJ Geewax (Manning, 2021)
- "Designing Web APIs" by Brenda Jin, Saurabh Sahni, Amir Shevat (O'Reilly, 2018)
- "Continuous API Management" by Mehdi Medjaoui et al. (O'Reilly, 2018)

### Articles and Guides
- "Best Practices in API Design" - Swagger
- "API Design Guide" - Google Cloud
- "RESTful API Design Tips" - Microsoft Azure
- "The OpenAPI Specification Explained" - Stoplight

### Specifications
- RFC 7807: Problem Details for HTTP APIs
- RFC 8288: Web Linking
- RFC 6570: URI Template

## License Information

This skill is based on publicly available standards and best practices:

- **OpenAPI Specification**: Apache License 2.0
- **OWASP Resources**: Creative Commons Attribution-ShareAlike 4.0
- **IETF RFCs**: Public domain (IETF Trust provisions)
- **JSON Schema**: Multiple licenses (BSD, MIT)
- **Industry guidelines**: Various (MIT, Apache 2.0, CC-BY-4.0)

All referenced materials are used in accordance with their respective licenses for educational and reference purposes.

---

**Note**: This skill provides guidance based on established standards and best practices. Always refer to the official specifications for authoritative information.

**Last Updated**: November 2025
**OpenAPI Version Covered**: 3.0.x and 3.1.x