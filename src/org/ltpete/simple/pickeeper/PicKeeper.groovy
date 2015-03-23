package org.ltpete.simple.pickeeper

import groovy.io.FileType

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory

class PicKeeper {

	static main(args) {
		def last
		def argsMap = args.collectEntries {
			[
				last = it ==~ "^--\\w+" ? it : last ,
				it
			]
		}

		
		def directory = argsMap["--directory"] ?: System.properties["user.dir"]
		def gallery = argsMap["--gallery"] ?: directory
		boolean recursive = argsMap["--recursive"]
		boolean cleanup = argsMap["--cleanup"] // emptied directories
		boolean shutdown = argsMap["--shutdown"]
		
		println "PicKeeper processing..."
		println "  from $directory${File.separator} (" + (recursive ? "" : "non-") +"recursive)"
		println "    to $gallery${File.separator}\${yyyy}${File.separator}\${yyyy-mm}${File.separator}"

		directory = new File(directory)
		gallery = new File(gallery)

		def process = { File file ->
			Long fileDate = file.lastModified()
			Long exifDate
			int orientation

			println file

			def extension = file.name.toLowerCase().split('\\.')[-1]
			def exifTypes = ["jpg", "jpeg"]
			
			if (extension in exifTypes) {
				try {
					Metadata metadata = ImageMetadataReader.readMetadata(file)
					if (metadata.getDirectory(ExifSubIFDDirectory)?.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
						exifDate = metadata.getDirectory(ExifSubIFDDirectory).getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)?.time
					}

					if (metadata.getDirectory(ExifIFD0Directory)?.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
						orientation = metadata.getDirectory(ExifIFD0Directory).getInt(ExifIFD0Directory.TAG_ORIENTATION) ?: 0
					}

					if (!exifDate) println "  no EXIF date"
					if (!orientation) println "  no EXIF orientation"
				} catch (Exception e) {
					println "  ERROR reading: " + e
				}
			} else {
				println "  skipping exif read"
			}

			if (orientation > 1) try {
				"jhead -autorot \"$file\"".execute().waitForProcessOutput(System.out, System.err)
			} catch (Exception e) {
				println "  ERROR rotating: " + e
			}

			Long lastModified = exifDate ?: fileDate
			try {
				file.setLastModified(lastModified)
			} catch (Exception e) {
				println "  ERROR setting date: " + e
			}

			File target = new File(gallery, new Date(lastModified).format("yyyy/yyyy-MM/") + file.name)
			if (file.path != target.path) try {
				def n = 1
				while (target.exists()) {
					target = new File(gallery, new Date(lastModified).format("yyyy/yyyy-MM/") + file.name.replaceFirst("(\\.[^.]*\$)", "_$n\$1"))
					n = n+1
				}

				target.parentFile.mkdirs()
				if (!file.renameTo(target)) {
					println "  ERROR can't move file to: $target"
				} else {
					println "  moved to: $target"
					if (cleanup && file.parentFile.list() == []) {
						file.parentFile.deleteDir()
					}
				}
			} catch (Exception e){
				println "  ERROR moving file: " + e
			}
		}

		recursive ? directory.eachFileRecurse(FileType.FILES, process) : directory.eachFile(FileType.FILES, process)

		println "\nFINISHED"
		
		if (shutdown) {
			"shutdown -s".execute()
		}
	}
}
