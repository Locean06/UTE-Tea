import Foundation
import PDFKit
import AppKit
let args=CommandLine.arguments
let input=URL(fileURLWithPath: args[1])
guard let doc=PDFDocument(url: input) else { fatalError("Cannot read PDF") }
print("PAGES=\(doc.pageCount)")
let folder=URL(fileURLWithPath: args[2]);try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
var all=""
for n in 0..<doc.pageCount { if let page=doc.page(at:n) { all += "\n\n--- PAGE \(n+1) ---\n"+(page.string ?? "") } }
try all.write(to:folder.appendingPathComponent("pdf_text.txt"),atomically:true,encoding:.utf8)
let selected=Set([0,1,2,3,4,8,12,16,20,25,30,40,60,80,100,120,140,160,180,200,220,240,doc.pageCount-2,doc.pageCount-1].filter{$0>=0 && $0<doc.pageCount})
for n in selected.sorted() {
 guard let page=doc.page(at:n) else {continue}
 let img=page.thumbnail(of:NSSize(width:1000,height:1415),for:.mediaBox)
 if let tiff=img.tiffRepresentation,let bitmap=NSBitmapImageRep(data:tiff),let png=bitmap.representation(using:.png,properties:[:]) {
  try png.write(to:folder.appendingPathComponent(String(format:"page_%03d.png",n+1)))
 }
}
