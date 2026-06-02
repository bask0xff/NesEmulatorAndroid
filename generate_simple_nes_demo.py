from pathlib import Path
import argparse
import base64
import importlib
import sys
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

header = bytearray(b'NES\x1A')
# 1 PRG bank, 0 CHR banks, mapper 0, horizontal mirroring
header += bytes([1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0])

prg = bytearray(16 * 1024)

code = [
    0x78,                   # SEI
    0xD8,                   # CLD
    0xA2, 0x00,             # LDX #$00
    0xA9, 0x00, 0x8D, 0x00, 0x20,  # STA $2000
    0xA9, 0x18, 0x8D, 0x01, 0x20,  # STA $2001
    0xA9, 0x3F, 0x8D, 0x06, 0x20,  # STA $2006 (high = $3F)
    0xA9, 0x00, 0x8D, 0x06, 0x20,  # STA $2006 (low = $00)
    0xA9, 0x0F, 0x8D, 0x07, 0x20,  # palette 0: universal bg color
    0xA9, 0x01, 0x8D, 0x07, 0x20,
    0xA9, 0x11, 0x8D, 0x07, 0x20,
    0xA9, 0x21, 0x8D, 0x07, 0x20,
    0xA9, 0x0F, 0x8D, 0x07, 0x20,
    0xA9, 0x16, 0x8D, 0x07, 0x20,
    0xA9, 0x26, 0x8D, 0x07, 0x20,
    0xA9, 0x36, 0x8D, 0x07, 0x20,
    0xA9, 0x05, 0x8D, 0x07, 0x20,

    # Set PPU address to $0000 and write two simple 8x16 tiles into CHR RAM.
    0xA9, 0x00, 0x8D, 0x06, 0x20,
    0xA9, 0x00, 0x8D, 0x06, 0x20,
    0xA2, 0x10,             # LDX #$10
    # plane 0 of tile 0 (solid block)
    0xA9, 0xFF,
    0x8D, 0x07, 0x20,
    0xCA,
    0xD0, 0xFD,
    # plane 1 of tile 0 (zero = color 1)
    0xA2, 0x10,
    0xA9, 0x00,
    0x8D, 0x07, 0x20,
    0xCA,
    0xD0, 0xFD,

    # Set PPU address to $0020 and write a second tile with a checker pattern.
    0xA9, 0x00, 0x8D, 0x06, 0x20,
    0xA9, 0x02, 0x8D, 0x06, 0x20,
    0xA2, 0x10,
    0xA9, 0xAA,
    0x8D, 0x07, 0x20,
    0xCA,
    0xD0, 0xFD,
    0xA2, 0x10,
    0xA9, 0x55,
    0x8D, 0x07, 0x20,
    0xCA,
    0xD0, 0xFD,

    # Set name table address to $2000 and fill the first 32 tiles.
    0xA9, 0x20, 0x8D, 0x06, 0x20,
    0xA9, 0x00, 0x8D, 0x06, 0x20,
    0xA2, 0x20,
    0xA9, 0x00,
    0x8D, 0x07, 0x20,
    0xCA,
    0xD0, 0xFB,

    # Set attribute table address to $23C0 and clear 8 bytes.
    0xA9, 0x23, 0x8D, 0x06, 0x20,
    0xA9, 0xC0, 0x8D, 0x06, 0x20,
    0xA2, 0x08,
    0xA9, 0x00,
    0x8D, 0x07, 0x20,
    0xCA,
    0xD0, 0xFB,

    # Infinite loop.
    0x4C, 0x00, 0x80,
]

prg[: len(code)] = bytes(code)
# Reset vector at end of PRG bank (use end of buffer so it works for 16KB or 32KB)
vec_off = len(prg) - 4
prg[vec_off] = 0x00
prg[vec_off + 1] = 0x80

rom_bytes = bytes(header) + bytes(prg)


def try_run_emulator(rom: bytes) -> bool:
    """Try to find a local emulator API and run the ROM from memory.
    Returns True if an attempt was made (and succeeded), False otherwise.
    """
    candidates = [
        ('emulator', 'run_rom_bytes'),
        ('nes_emulator', 'run_rom_bytes'),
        ('nes_emulator', 'run'),
        ('emu', 'run_rom'),
        ('emulator', 'run'),
        ('emulator', 'load_rom'),
    ]
    for mod_name, fn_name in candidates:
        try:
            mod = importlib.import_module(mod_name)
        except Exception:
            continue
        fn = getattr(mod, fn_name, None)
        if callable(fn):
            try:
                # Try calling with rom bytes directly
                fn(rom)
                print(f'Ran ROM with {mod_name}.{fn_name}')
                return True
            except TypeError:
                # Try calling with named parameter
                try:
                    fn(rom_bytes=rom)
                    print(f'Ran ROM with {mod_name}.{fn_name} (rom_bytes=)')
                    return True
                except Exception as e:
                    print(f'Failed to call {mod_name}.{fn_name}: {e}')
                    return False
            except Exception as e:
                print(f'Error running emulator {mod_name}.{fn_name}: {e}')
                return False
    return False


class ROMHandler(BaseHTTPRequestHandler):
    ROM_PATH = '/simple-demo.nes'
    rom_data = b''

    def do_GET(self):
        if self.path != self.ROM_PATH:
            self.send_response(404)
            self.end_headers()
            return
        self.send_response(200)
        self.send_header('Content-Type', 'application/octet-stream')
        self.send_header('Content-Length', str(len(self.rom_data)))
        self.end_headers()
        self.wfile.write(self.rom_data)


def serve_rom(port: int, rom: bytes):
    ROMHandler.rom_data = rom
    server = HTTPServer(('127.0.0.1', port), ROMHandler)

    def run():
        print(f'Serving ROM at http://127.0.0.1:{port}/simple-demo.nes')
        try:
            server.serve_forever()
        except Exception:
            pass

    t = threading.Thread(target=run, daemon=True)
    t.start()
    return server


def main(argv=None):
    parser = argparse.ArgumentParser(description='Generate simple-demo NES ROM')
    parser.add_argument('--no-save', action='store_true', help='Do not save ROM to assets')
    parser.add_argument('--serve', type=int, metavar='PORT', help='Serve ROM over HTTP on PORT')
    parser.add_argument('--print-b64', action='store_true', help='Print base64 of ROM to stdout')
    parser.add_argument('--auto-run', action='store_true', help='Try to auto-run using local emulator API')
    args = parser.parse_args(argv)

    if args.no_save:
        if args.auto_run:
            ran = try_run_emulator(rom_bytes)
            if ran:
                return
        if args.serve:
            server = serve_rom(args.serve, rom_bytes)
            try:
                # Keep running until stopped by user
                print('Press Ctrl-C to stop serving')
                server.serve_forever()
            except KeyboardInterrupt:
                server.shutdown()
                print('Server stopped')
            return
        if args.print_b64:
            print(base64.b64encode(rom_bytes).decode())
            return

    # Default behavior: write to assets and report path
    out = Path('app/src/main/assets/simple-demo.nes')
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open('wb') as f:
        f.write(rom_bytes)

    print(f'Wrote {out.resolve()} ({out.stat().st_size} bytes)')


if __name__ == '__main__':
    main()
