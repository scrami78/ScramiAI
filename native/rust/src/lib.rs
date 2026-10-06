use sha2::{Digest, Sha256};

pub fn stable_key(input: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(input.as_bytes());
    format!("{:x}", hasher.finalize())
}

#[no_mangle]
pub extern "C" fn sai_key_len(ptr: *const u8, len: usize) -> usize {
    if ptr.is_null() { return 0; }
    let bytes = unsafe { std::slice::from_raw_parts(ptr, len) };
    let mut h = Sha256::new();
    h.update(bytes);
    64
}

#[cfg(test)]
mod tests {
    use super::stable_key;

    #[test]
    fn key_is_deterministic() {
        assert_eq!(stable_key("sai"), stable_key("sai"));
        assert_ne!(stable_key("sai"), stable_key("other"));
    }
}
