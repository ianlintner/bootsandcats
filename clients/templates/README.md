# Custom Templates Directory

This directory contains custom Mustache templates for OpenAPI Generator.

## Usage

Place custom templates in language-specific subdirectories:

```
templates/
├── typescript/
│   ├── apiInner.mustache
│   └── modelGeneric.mustache
├── python/
│   └── api.mustache
├── go/
│   └── api.mustache
└── rust/
    └── api.mustache
```

## Getting Default Templates

To get the default templates for customization:

```bash
# TypeScript
openapi-generator-cli author template -g typescript-axios -o templates/typescript

# Python
openapi-generator-cli author template -g python -o templates/python

# Go
openapi-generator-cli author template -g go -o templates/go

# Rust
openapi-generator-cli author template -g rust -o templates/rust
```

## Using Custom Templates

Update the configuration files to use custom templates:

```yaml
# In config/typescript.yaml
templateDir: ../templates/typescript
```

## Common Customizations

### Adding OpenTelemetry Tracing

You can modify the API templates to automatically add tracing spans around API calls.

### Custom Error Handling

Modify exception/error templates to add custom error handling logic.

### Adding Retry Logic

Add retry logic to HTTP client templates for better resilience.
