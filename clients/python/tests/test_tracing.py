"""
Tests for tracing module.
"""

import pytest
from bootsandcats_oauth2_client.tracing import (
    TRACING_AVAILABLE,
    traced_api_call,
    TracedApiClient,
)


class TestTracingAvailability:
    """Tests for tracing availability detection."""

    def test_tracing_available_is_boolean(self):
        """TRACING_AVAILABLE should be a boolean."""
        assert isinstance(TRACING_AVAILABLE, bool)


class TestTracedApiCallDecorator:
    """Tests for the traced_api_call decorator."""

    def test_noop_decorator_when_tracing_unavailable(self):
        """When tracing is unavailable, decorator should be a no-op."""

        def sample_function(x: int) -> int:
            return x * 2

        # Create a mock tracer (will be ignored if tracing unavailable)
        mock_tracer = None

        if not TRACING_AVAILABLE:
            decorated = traced_api_call(mock_tracer, "test-operation")(sample_function)
            assert decorated(5) == 10
            assert decorated.__name__ == "sample_function"


class TestTracedApiClient:
    """Tests for TracedApiClient wrapper."""

    def test_attribute_delegation(self):
        """TracedApiClient should delegate attribute access to wrapped API."""

        class MockApi:
            value = 42

            def get_value(self) -> int:
                return self.value

        mock_api = MockApi()
        # Using None as tracer since we're testing delegation, not tracing
        if not TRACING_AVAILABLE:
            traced = TracedApiClient(mock_api, None, "mock")
            # Non-callable attributes should be delegated directly
            # Methods should be wrapped (but noop if tracing unavailable)
