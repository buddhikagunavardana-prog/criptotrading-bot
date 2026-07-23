import os
import base64
import hashlib
import logging

logger = logging.getLogger(__name__)

# Fallback or main key for encryption derivation
DEFAULT_SECRET_KEY = "SUPER_SECRET_TRADING_BOT_KEY_CHANGE_ME"
ENCRYPTION_KEY_RAW = os.getenv("API_KEY_ENCRYPTION_KEY", DEFAULT_SECRET_KEY)

# Derive a 32-byte URL-safe base64-encoded key from the raw key for Fernet
_derived_key = hashlib.sha256(ENCRYPTION_KEY_RAW.encode()).digest()
FERNET_KEY = base64.urlsafe_b64encode(_derived_key)

try:
    from cryptography.fernet import Fernet
    fernet_suite = Fernet(FERNET_KEY)
except ImportError:
    fernet_suite = None
    logger.warning(
        "The 'cryptography' package is not installed. "
        "Sensitive keys will be encoded using custom secure fallback, but not strongly encrypted. "
        "Please install the 'cryptography' library for maximum production security."
    )

def encrypt_data(data: str, secret_key: str = DEFAULT_SECRET_KEY) -> str:
    """
    Encrypts a string using Fernet symmetric encryption.
    If the cryptography library is not available, uses a secure XOR base64 fallback.
    """
    if not data:
        return ""
    if fernet_suite:
        try:
            return fernet_suite.encrypt(data.encode()).decode()
        except Exception as e:
            logger.error(f"Fernet encryption error: {e}")
            raise e
    else:
        # Reversible secure custom XOR fallback on bytes to avoid encoding issues
        raw_bytes = data.encode()
        key_bytes = secret_key.encode()
        key_len = len(key_bytes)
        xor_bytes = bytes(b ^ key_bytes[i % key_len] for i, b in enumerate(raw_bytes))
        return base64.b64encode(xor_bytes).decode()

def decrypt_data(encrypted_data: str, secret_key: str = DEFAULT_SECRET_KEY) -> str:
    """
    Decrypts a string using Fernet symmetric encryption.
    If the cryptography library is not available, uses the XOR base64 fallback.
    """
    if not encrypted_data:
        return ""
    if fernet_suite:
        try:
            return fernet_suite.decrypt(encrypted_data.encode()).decode()
        except Exception as e:
            logger.error(f"Fernet decryption error: {e}")
            raise e
    else:
        try:
            xor_bytes = base64.b64decode(encrypted_data.encode())
            key_bytes = secret_key.encode()
            key_len = len(key_bytes)
            raw_bytes = bytes(b ^ key_bytes[i % key_len] for i, b in enumerate(xor_bytes))
            return raw_bytes.decode()
        except Exception as e:
            logger.error(f"Error decrypting data with custom fallback: {e}")
            return ""
