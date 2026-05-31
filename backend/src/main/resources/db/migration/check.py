import binascii

with open('V30__aftersale_refund_traceability.sql', 'rb') as f:
    content = f.read()
    crc = binascii.crc32(content)
    # Convert to signed 32-bit integer as Java/Flyway uses
    if crc > 0x7FFFFFFF:
        crc -= 1 << 32
    print(crc)
