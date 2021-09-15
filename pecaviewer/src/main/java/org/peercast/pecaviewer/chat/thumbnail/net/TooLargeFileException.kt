package org.peercast.pecaviewer.chat.thumbnail.net

import java.io.IOException

class TooLargeFileException(val size: Int) : IOException("large file: ${size / 1024}KB")