package com.jeffdisher.october.persistence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * Logic to convert the storage directory from version <= 11 to version >= 12.
 * This deals with the entire data store at once so it is potentially expensive and may have some gaps where the update
 * isn't perfectly atomic (although this design should cover these cases).
 * Phases of update:
 * 1) detect if update is required (done by checking if old data files exist in the old root)
 * 2) clear new directories in case there was a partial update (done outside)
 * 3) check for backup zip in old root directory:
 *  -if present and contains a file called "DONE", then proceed to step (5)
 *  -is present and does NOT contain file called "DONE", delete backup and proceed to step (4)
 * 4) create backup zip, copy all old files into it, end by creating empty entry called "DONE" and flushing/closing zip
 * 5) read backup zip file and populate new CuboidClusterManager (updating each entry) or copying to entity directory.
 * 6) delete all old files in root directory
 * 
 * At this point, the update is complete and the lack of old files means it will not be triggered again.  The backup is
 * left behind but can be deleted by the user if they like.
 */
public class StorageModelMigration
{
	public static final String BACKUP_FILE_NAME = "BACKUP_pre12.zip";
	public static final String DONE_ENTRY_NAME = "DONE";
	public static final String PREFIX_CUBOID = "cuboid_";
	public static final String PREFIX_ENTITY = "entity_";

	/**
	 * Returns true if the shape of data files found in oldRoot implies that it contains pre-migration data files.
	 * 
	 * @param oldRoot The root directory for the storage model.
	 * @return True if this must be migrated to the new shape.
	 */
	public static boolean requiresMigration(File oldRoot)
	{
		boolean requiresMigration = false;
		for (File sub : oldRoot.listFiles())
		{
			String name = sub.getName();
			if (name.startsWith(PREFIX_CUBOID) || name.startsWith(PREFIX_ENTITY))
			{
				requiresMigration = true;
				break;
			}
		}
		return requiresMigration;
	}

	/**
	 * Performs the slow operation of migrating storage from the flat shape to the new shape.
	 * 
	 * @param oldRoot The root directory for the storage model.
	 * @param entityDirectory The directory where the entity files should be moved (must exist).
	 * @param cuboidClusterManager The directory where the cluster manager should base its region directories (must
	 * exist).
	 * @param scratchBuffer A scratch buffer to use during the transformation (must be clear).
	 */
	public static void migrateStorage(File oldRoot
		, File entityDirectory
		, CuboidClusterManager cuboidClusterManager
		, ByteBuffer scratchBuffer
	)
	{
		File backupZipFile = new File(oldRoot, BACKUP_FILE_NAME);
		
		// Step 3.
		boolean skipBackupCreation = _isBackupComplete(backupZipFile);
		
		// Step 4.
		if (!skipBackupCreation)
		{
			_populateZipFromScratch(oldRoot, backupZipFile);
		}
		
		// Step 5.
		_readAndUpdateFromZip(backupZipFile, cuboidClusterManager, entityDirectory, scratchBuffer);
		
		// Step 6.
		_deleteOldFiles(oldRoot);
	}


	private static boolean _isBackupComplete(File backupZipFile)
	{
		// Check if the DONE marker file is present in the zip.
		boolean isComplete = false;
		if (backupZipFile.exists())
		{
			try (
				ZipFile zipFile = new ZipFile(backupZipFile);
			)
			{
				isComplete = (null != zipFile.getEntry(DONE_ENTRY_NAME));
			}
			catch (FileNotFoundException e)
			{
				// Not expected here since we should be creating the file.
				Assert.unexpected(e);
			}
			catch (IOException e)
			{
				// This is a fatal error, at this point, so we should just fail out.
				Assert.unexpected(e);
			}
		}
		return isComplete;
	}

	private static void _populateZipFromScratch(File oldRoot, File backupZipFile)
	{
		if (backupZipFile.exists())
		{
			boolean didDelete = backupZipFile.delete();
			Assert.assertTrue(didDelete);
		}
		try (
			ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(backupZipFile));
		)
		{
			for (File sub : oldRoot.listFiles())
			{
				String name = sub.getName();
				if (name.startsWith(PREFIX_CUBOID) || name.startsWith(PREFIX_ENTITY))
				{
					// We need to import this into the zip.
					byte[] buffer = Files.readAllBytes(sub.toPath());
					
					ZipEntry entry = new ZipEntry(name);
					entry.setSize(buffer.length);
					
					zipOut.putNextEntry(entry);
					zipOut.write(buffer);
					zipOut.closeEntry();
				}
			}
		}
		catch (FileNotFoundException e)
		{
			// Not expected here since we should be creating the file.
			Assert.unexpected(e);
		}
		catch (IOException e)
		{
			// This is a fatal error, at this point, so we should just fail out.
			Assert.unexpected(e);
		}
	}

	private static void _readAndUpdateFromZip(File backupZipFile
		, CuboidClusterManager cuboidClusterManager
		, File entityDirectory
		, ByteBuffer scratchBuffer
	)
	{
		// Note that we need to use ZipFile as ZipInputStream doesn't give us correct sizes, even though we set them, inline, when creating the file, above.
		try (
			ZipFile zipFile = new ZipFile(backupZipFile);
		)
		{
			Enumeration<? extends ZipEntry> enumer = zipFile.entries();
			while (enumer.hasMoreElements())
			{
				ZipEntry entry = enumer.nextElement();
				String name = entry.getName();
				long uncompressedSize = entry.getSize();
				
				byte[] buffer = new byte[(int)uncompressedSize];
				int read = 0;
				try (
					InputStream entryInput = zipFile.getInputStream(entry);
				)
				{
					while (read < buffer.length)
					{
						int one = entryInput.read(buffer, read, buffer.length - read);
						Assert.assertTrue(one > 0);
						read += one;
					}
					Assert.assertTrue(0 == entryInput.available());
				}
				
				if (name.startsWith(PREFIX_CUBOID))
				{
					// Plumb this into the CuboidCluserManger.
					ByteBuffer inBuffer = ByteBuffer.wrap(buffer);
					int version = inBuffer.getInt();
					
					// We expect to parse into an empty buffer and should fully drain the input buffer.
					Assert.assertTrue(0 == scratchBuffer.position());
					CuboidTranslator.changeToLatestVersion(scratchBuffer
						, inBuffer
						, version
					);
					Assert.assertTrue(0 == inBuffer.remaining());
					
					// Copy out this data and pass it to the cluster manager.
					byte[] updatedData = new byte[scratchBuffer.position()];
					scratchBuffer.flip();
					scratchBuffer.get(updatedData);
					scratchBuffer.clear();
					
					CuboidAddress address = _parseAddressFromName(name);
					byte[] empty = cuboidClusterManager.readCuboid(address);
					Assert.assertTrue(null == empty);
					cuboidClusterManager.writeCuboid(address, updatedData, false);
				}
				else if (name.startsWith(PREFIX_ENTITY))
				{
					// We want to take this opportunity to convert the data to the new shape, since we are touching it.
					ByteBuffer inBuffer = ByteBuffer.wrap(buffer);
					int version = inBuffer.getInt();
					
					// We expect to parse into an empty buffer and should fully drain the input buffer.
					Assert.assertTrue(0 == scratchBuffer.position());
					// (unlike cuboids, entities still store version numbers inline)
					scratchBuffer.putInt(StorageVersions.CURRENT);
					EntityTranslator.changeToLatestVersion(scratchBuffer
						, inBuffer
						, version
					);
					Assert.assertTrue(0 == inBuffer.remaining());
					
					// Copy out this data and write it to disk (this copy is arguably redundant but keeps the shape the same as cuboids).
					byte[] updatedData = new byte[scratchBuffer.position()];
					scratchBuffer.flip();
					scratchBuffer.get(updatedData);
					scratchBuffer.clear();
					
					// Write this as a file in the new entity directory.
					File newFile = new File(entityDirectory, name);
					Files.write(newFile.toPath(), updatedData, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
				}
				else
				{
					// The only other thing here is the DONE.
					Assert.assertTrue(name.equals(DONE_ENTRY_NAME));
				}
			}
		}
		catch (FileNotFoundException e)
		{
			// Not expected here since we should be creating the file and we know the directories exist.
			throw Assert.unexpected(e);
		}
		catch (IOException e)
		{
			// This is a fatal error, at this point, so we should just fail out.
			throw Assert.unexpected(e);
		}
	}

	private static CuboidAddress _parseAddressFromName(String fileName)
	{
		// The shape of the string is "cuboid_X_Y_Z.cuboid" so we want to split by "." and then split by "_".
		String[] segments = fileName.split("\\.")[0].split("_");
		short x = Short.parseShort(segments[1]);
		short y = Short.parseShort(segments[2]);
		short z = Short.parseShort(segments[3]);
		return new CuboidAddress(x, y, z);
	}

	private static void _deleteOldFiles(File oldRoot)
	{
		for (File sub : oldRoot.listFiles())
		{
			String name = sub.getName();
			if (name.startsWith(PREFIX_CUBOID) || name.startsWith(PREFIX_ENTITY))
			{
				// This was involved in the update so delete it.
				boolean didDelete = sub.delete();
				Assert.assertTrue(didDelete);
			}
		}
	}
}
