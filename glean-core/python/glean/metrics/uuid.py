# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.


from typing import List, Optional
import uuid


from .. import _ffi
from .._dispatcher import Dispatcher
from ..testing import ErrorType


from .lifetime import Lifetime


class UuidMetricType:
    """
    This implements the developer facing API for recording UUID metrics.

    Instances of this class type are automatically generated by
    `glean.load_metrics`, allowing developers to record values that were
    previously registered in the metrics.yaml file.

    The UUID API exposes the `UuidMetricType.generate_and_set` and
    `UuidMetricType.set` methods.
    """

    def __init__(
        self,
        disabled: bool,
        category: str,
        lifetime: Lifetime,
        name: str,
        send_in_pings: List[str],
    ):
        self._disabled = disabled
        self._send_in_pings = send_in_pings

        self._handle = _ffi.lib.glean_new_uuid_metric(
            _ffi.ffi_encode_string(category),
            _ffi.ffi_encode_string(name),
            _ffi.ffi_encode_vec_string(send_in_pings),
            len(send_in_pings),
            lifetime.value,
            disabled,
        )

    def __del__(self):
        if getattr(self, "_handle", 0) != 0:
            _ffi.lib.glean_destroy_uuid_metric(self._handle)

    def generate_and_set(self) -> Optional[uuid.UUID]:
        """
        Generate a new UUID value and set it in the metric store.
        """
        if self._disabled:
            return None

        id = uuid.uuid4()
        self.set(id)
        return id

    def set(self, value: uuid.UUID) -> None:
        """
        Explicitly set an existing UUID value.

        Args:
            value (uuid.UUID): A valid UUID to set the metric to.
        """
        if self._disabled:
            return

        @Dispatcher.launch
        def set():
            _ffi.lib.glean_uuid_set(self._handle, _ffi.ffi_encode_string(str(value)))

    def test_has_value(self, ping_name: Optional[str] = None) -> bool:
        """
        Tests whether a value is stored for the metric for testing purposes
        only.

        Args:
            ping_name (str): (default: first value in send_in_pings) The name
                of the ping to retrieve the metric for.

        Returns:
            has_value (bool): True if the metric value exists.
        """
        if ping_name is None:
            ping_name = self._send_in_pings[0]

        return bool(
            _ffi.lib.glean_uuid_test_has_value(
                self._handle, _ffi.ffi_encode_string(ping_name)
            )
        )

    def test_get_value(self, ping_name: Optional[str] = None) -> uuid.UUID:
        """
        Returns the stored value for testing purposes only.

        Args:
            ping_name (str): (default: first value in send_in_pings) The name
                of the ping to retrieve the metric for.

        Returns:
            value (int): value of the stored metric.
        """
        if ping_name is None:
            ping_name = self._send_in_pings[0]

        if not self.test_has_value(ping_name):
            raise ValueError("metric has no value")

        return uuid.UUID(
            "urn:uuid:"
            + _ffi.ffi_decode_string(
                _ffi.lib.glean_uuid_test_get_value(
                    self._handle, _ffi.ffi_encode_string(ping_name)
                )
            )
        )

    def test_get_num_recorded_errors(
        self, error_type: ErrorType, ping_name: Optional[str] = None
    ) -> int:
        """
        Returns the number of errors recorded for the given metric.

        Args:
            error_type (ErrorType): The type of error recorded.
            ping_name (str): (default: first value in send_in_pings) The name
                of the ping to retrieve the metric for.

        Returns:
            num_errors (int): The number of errors recorded for the metric for
                the given error type.
        """
        if ping_name is None:
            ping_name = self._send_in_pings[0]

        return _ffi.lib.glean_uuid_test_get_num_recorded_errors(
            self._handle, error_type.value, _ffi.ffi_encode_string(ping_name),
        )


__all__ = ["UuidMetricType"]
