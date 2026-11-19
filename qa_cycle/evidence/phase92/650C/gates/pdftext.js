ObjC.import('PDFKit');
function run(argv) { const d = $.PDFDocument.alloc.initWithURL($.NSURL.fileURLWithPath(argv[0])); if (!d || d.isNil()) return "ERR: cannot open"; return ObjC.unwrap(d.string); }
