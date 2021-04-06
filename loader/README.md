# Readme
LSPosed as hook framework

There some major change since xpatch

1. use LSPosed as hook framework
1. keep loader simple, clear not nesseacry things, like bypass signature. let developer do this part


`Maybe perform force push if some private data leak in project. Sorry for the confusion.`

# Useage
1. You need do signature bypass by yourself
1. Orignal signature saved to assets/original_signature_info.ini
1. Orignal apk saved to assets/original_apk.bin

For example, you may need to replace signatures from getPackageInfo and redirect `/data/app/{your apk}/base.apk` to `original_apk.bin` to bypass normally signature check.