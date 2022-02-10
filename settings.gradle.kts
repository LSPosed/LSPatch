rootProject.name = "LSPatch"

include(":daemon-service")
include(":hiddenapi-bridge")
include(":hiddenapi-stubs")
include(":interface")
include(":lspcore")
include(":manager-service")

project(":daemon-service").projectDir = file("core/daemon-service")
project(":hiddenapi-bridge").projectDir = file("core/hiddenapi-bridge")
project(":hiddenapi-stubs").projectDir = file("core/hiddenapi-stubs")
project(":interface").projectDir = file("core/service/interface")
project(":lspcore").projectDir = file("core/core")
project(":manager-service").projectDir = file("core/manager-service")

include(":apkzlib")
include(":app")
include(":appstub")
include(":axmlprinter")
include(":imanager")
include(":manager")
include(":patch")
include(":share")
