package tikrana.util

import java.io.File

object Types:
  type Millis = Long

  type Directory = File
  type Filename = String
  type DirectoryName = String
  type Extension = String
  type ByteArray = Array[Byte]

  type NetPort = Int
  type NetAddress = String
end Types