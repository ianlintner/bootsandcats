//! OpenTelemetry tracing integration for the OAuth2 client.

use opentelemetry::global;
use opentelemetry_otlp::WithExportConfig;
use opentelemetry_sdk::{runtime, trace as sdktrace, Resource};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

/// Initialize OpenTelemetry tracing.
///
/// # Arguments
///
/// * `service_name` - Name of the service for tracing
/// * `service_version` - Version of the service
/// * `exporter_url` - OTLP exporter URL
///
/// # Example
///
/// ```rust,no_run
/// use bootsandcats_oauth2_client::tracing::init_tracing;
///
/// fn main() -> Result<(), Box<dyn std::error::Error>> {
///     init_tracing("my-app", "1.0.0", "http://localhost:4318/v1/traces")?;
///     // ... your code
///     Ok(())
/// }
/// ```
pub fn init_tracing(
    service_name: &str,
    service_version: &str,
    exporter_url: &str,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    // Create resource
    let resource = Resource::new(vec![
        opentelemetry::KeyValue::new("service.name", service_name.to_string()),
        opentelemetry::KeyValue::new("service.version", service_version.to_string()),
    ]);

    // Create OTLP exporter
    let exporter = opentelemetry_otlp::new_exporter()
        .http()
        .with_endpoint(exporter_url);

    // Create tracer
    let tracer = opentelemetry_otlp::new_pipeline()
        .tracing()
        .with_exporter(exporter)
        .with_trace_config(sdktrace::config().with_resource(resource))
        .install_batch(runtime::Tokio)?;

    // Create telemetry layer
    let telemetry_layer = tracing_opentelemetry::layer().with_tracer(tracer);

    // Create env filter
    let env_filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info"));

    // Initialize subscriber
    tracing_subscriber::registry()
        .with(env_filter)
        .with(telemetry_layer)
        .with(tracing_subscriber::fmt::layer())
        .init();

    Ok(())
}

/// Shutdown OpenTelemetry tracing.
///
/// Should be called when the application exits to ensure all spans are exported.
pub fn shutdown_tracing() {
    global::shutdown_tracer_provider();
}

/// Get the current tracer.
pub fn tracer() -> opentelemetry::global::BoxedTracer {
    global::tracer("bootsandcats-oauth2-client")
}

/// Instrument a future with tracing.
///
/// # Example
///
/// ```rust,ignore
/// use bootsandcats_oauth2_client::tracing::instrument;
///
/// async fn my_function() {
///     let _span = instrument("my-operation");
///     // ... your async code
/// }
/// ```
#[macro_export]
macro_rules! instrument {
    ($name:expr) => {
        tracing::info_span!($name)
    };
    ($name:expr, $($field:tt)*) => {
        tracing::info_span!($name, $($field)*)
    };
}

pub use instrument;
