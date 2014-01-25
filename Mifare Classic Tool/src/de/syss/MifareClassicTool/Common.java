/*
 * Copyright 2013 Gerhard Klostermeier
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package de.syss.MifareClassicTool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

/**
 * Common functions and variables for all Activities.
 * @author Gerhard Klostermeier
 */
public class Common extends Application {

    /**
     * The directory name of the root directory of this app
     * (on external storage).
     */
    public static final String HOME_DIR = "/MifareClassicTool";

    /**
     * The directory name  of the key files directory.
     * (sub directory of {@link #HOME_DIR}.)
     */
    public static final String KEYS_DIR = "key-files";

    /**
     * The directory name  of the dump files directory.
     * (sub directory of {@link #HOME_DIR}.)
     */
    public static final String DUMPS_DIR = "dump-files";

    /**
     * The directory name of the folder where temporary files are
     * stored. The directory will be cleaned during the creation of
     * the main activity ({@link MainMenu.MainMenu}).
     * (sub directory of {@link #HOME_DIR}.)
     */
    public static final String TMP_DIR = "tmp";

    /**
     * This file contains some standard Mifare keys.
     * <ul>
     * <li>0xFFFFFFFFFFFF - Unformatted, factory fresh tags.</li>
     * <li>0xA0A1A2A3A4A5 - First sector of the tag (Mifare MAD).</li>
     * <li>0xD3F7D3F7D3F7 - NDEF formated tags.</li>
     * </ul>
     */
    public static final String STD_KEYS = "std.keys";

    /**
     * Keys taken from SLURP by Anders Sundman anders@4zm.org
     * (and a short google search).
     * https://github.com/4ZM/slurp/blob/master/res/xml/mifare_default_keys.xml
     */
    public static final String STD_KEYS_EXTENDED = "extended-std.keys";

    /**
     * Possible operations the on a Mifare Classic Tag.
     */
    public enum Operations {
        Read, Write, Increment, DecTransRest, ReadKeyA, ReadKeyB, ReadAC,
        WriteKeyA, WriteKeyB, WriteAC
    }

    private static final String LOG_TAG = Common.class.getSimpleName();

    /**
     * The last detected tag.
     * Set by {@link #treatAsNewTag(Intent, Context)}
     */
    private static Tag mTag = null;

    /**
     * The last detected UID.
     * Set by {@link #treatAsNewTag(Intent, Context)}
     */
    private static byte[] mUID = null;

    /**
     * Just a global storage to save key maps generated by
     * {@link CreateKeyMap.CreateKeyMap}
     * @see CreateKeyMap.CreateKeyMap
     * @see MCReader#getKeyMap()
     */
    private static SparseArray<byte[][]> mKeyMap = null;

    /**
     * Global storage for the point where
     * {@link CreateKeyMap.CreateKeyMap} started to create a key map.
     * @see CreateKeyMap.CreateKeyMap
     * @see MCReader#getKeyMap()
     */
    private static int mKeyMapFrom = -1;

    /**
     * Global storage for the point where
     * {@link CreateKeyMap.CreateKeyMap} ended to create a key map.
     * @see CreateKeyMap.CreateKeyMap
     * @see MCReader#getKeyMap()
     */
    private static int mKeyMapTo = -1;

    /**
     * The version code from the Android manifest.
     */
    private static String mVersionCode;

    private static NfcAdapter mNfcAdapter;
    private static Context mAppContext;

// ############################################################################

    /**
     * Initialize the {@link #mAppContext} with the application context
     * (for {@link #getPreferences()) and {@link #mVersionCode}.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mAppContext = getApplicationContext();

        try {
            mVersionCode = getPackageManager().getPackageInfo(
                    getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            Log.d(LOG_TAG, "Version not found.");
        }
    }

    /**
     * Checks if external storage is available for read and write.
     * If not, show an error Toast.
     * @param context The Context in which the Toast will be shown.
     * @return True if external storage is writable. False otherwise.
     */
    public static boolean isExternalStorageWritableErrorToast(
            Context context) {
        if (Environment.MEDIA_MOUNTED.equals(
                Environment.getExternalStorageState())) {
            return true;
        }
        Toast.makeText(context, R.string.info_no_external_storage,
                Toast.LENGTH_LONG).show();
        return false;
    }

    /**
     * Read a file line by line. The file should be a simple text file.
     * Empty lines and lines STARTING with "#" will not be interpreted.
     * @param file The file to read.
     * @param readComments Whether to read comments or to ignore them.
     * Comments are lines STARTING with "#" (and empty lines).
     * @param context  The context in which the possible "Out of memory"-Toast
     * will be shown.
     * @return Array of strings representing the lines of the file.
     * If the file is empty or an error occurs "null" will be returned.
     */
    public static String[] readFileLineByLine(File file, boolean readComments,
            Context context) {
        BufferedReader br = null;
        String[] ret = null;
        if (file != null && file.exists()) {
            try {
                br = new BufferedReader(new FileReader(file));

                String line;
                ArrayList<String> linesArray = new ArrayList<String>();
                while ((line = br.readLine()) != null)   {
                    // Ignore empty lines.
                    // Ignore comments if readComments == false.
                    if ( !line.equals("")
                            && (readComments || !line.startsWith("#"))) {
                        try {
                            linesArray.add(line);
                        } catch (OutOfMemoryError e) {
                            // Error. File is too big
                            // (too many lines, out of memory).
                            Toast.makeText(context, R.string.info_file_to_big,
                                    Toast.LENGTH_LONG).show();
                            return null;
                        }
                    }
                }
                if (linesArray.size() > 0) {
                    ret = linesArray.toArray(new String[linesArray.size()]);
                } else {
                    ret = new String[] {""};
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while reading from file "
                        + file.getPath() + "." ,e);
                ret = null;
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    }
                    catch (IOException e) {
                        Log.e(LOG_TAG, "Error while closing file.", e);
                        ret = null;
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Write an array of strings (each field is one line) to a given file.
     * If the file already exists, it will be overwritten.
     * @param file The file to write to.
     * @param lines The lines to save.
     * @return True if file writing was successful. False otherwise.
     */
    public static boolean saveFile(File file, String[] lines) {
        boolean noError = true;
        if (file != null) {
            BufferedWriter bw = null;
            try {
                bw = new BufferedWriter(new FileWriter(file));
                int i;
                for(i = 0; i < lines.length-1; i++){
                    bw.write(lines[i]);
                    bw.newLine();
               }
               bw.write(lines[i]);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error while writing to '"
                        + file.getName() + "' file.", e);
                noError = false;

            } finally {
                if (bw != null) {
                    try {
                        bw.close();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Error while closing file.", e);
                        noError = false;
                    }
                }
            }
        } else {
            noError = false;
        }
        return noError;
    }

    /**
     * Get the shared preferences with application context for saving
     * and loading ("global") values.
     * @return The shared preferences object with application context.
     */
    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mAppContext);
    }

    /**
     * Enables the NFC foreground dispatch system for the given Activity.
     * @param targetActivity The Activity that is in foreground and wants to
     * have NFC Intents.
     * @see #disableNfcForegroundDispatch(Activity)
     */
    public static void enableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {

            Intent intent = new Intent(targetActivity,
                    targetActivity.getClass()).addFlags(
                            Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    targetActivity, 0, intent, 0);
            mNfcAdapter.enableForegroundDispatch(
                    targetActivity, pendingIntent, null, new String[][] {
                            new String[] { NfcA.class.getName() } });
        }
    }

    /**
     * Disable the NFC foreground dispatch system for the given Activity.
     * @param targetActivity An Activity that is in foreground and has
     * NFC foreground dispatch system enabled.
     * @see #enableNfcForegroundDispatch(Activity)
     */
    public static void disableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {
            mNfcAdapter.disableForegroundDispatch(targetActivity);
        }
    }

    /**
     * For Activities which want to treat new Intents as Intents with a new
     * Tag attached. If the given Intent has a Tag extra, the
     * {@link #mTag} and {@link #mUID} will be updated and a Toast
     * message will be shown in the calling Context (Activity).
     * This method will also check if the device/tag supports Mifare Classic
     * (see return values and {@link #checkMifareClassicSupport(Tag, Context)}).
     * @param intent The Intent which should be checked for a new Tag.
     * @param context The Context in which the Toast will be shown.
     * @return
     * <ul>
     * <li>0 - The device/tag supports Mifare Classic</li>
     * <li>-1 - Device does not support Mifare Classic.</li>
     * <li>-2 - Tag does not support Mifare Classic.</li>
     * <li>-3 - Error (tag or context is null).</li>
     * <li>-4 - Wrong Intent (action is not "ACTION_TECH_DISCOVERED").</li>
     * </ul>
     * @see #mTag
     * @see #mUID
     * @see #checkMifareClassicSupport(Tag, Context)
     */
    public static int treatAsNewTag(Intent intent, Context context) {
        // Check if Intent has a NFC Tag.
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            setTag(tag);

            // Show Toast message with UID.
            String id = context.getResources().getString(
                    R.string.info_new_tag_found) + " (UID: ";
            id += byte2HexString(tag.getId());
            id += ")";
            Toast.makeText(context, id, Toast.LENGTH_LONG).show();
            return checkMifareClassicSupport(tag, context);
        }
        return -4;
    }

    /**
     * Check if the tag and the device support the Mifare Classic technology.
     * @param tag The tag to check.
     * @param context The context of the package manager.
     * @return
     * <ul>
     * <li>0 - Device and tag support Mifare Classic.</li>
     * <li>-1 - Device does not support Mifare Classic.</li>
     * <li>-2 - Tag does not support Mifare Classic.</li>
     * <li>-3 - Error (tag or context is null).</li>
     * </ul>
     */
    public static int checkMifareClassicSupport(Tag tag, Context context) {
        if (tag == null || context == null) {
            // Error.
            return -3;
        }

        if (Arrays.asList(tag.getTechList()).contains(
                MifareClassic.class.getName())) {
            // Device and tag support Mifare Classic.
            return 0;
        } else if (context.getPackageManager().hasSystemFeature(
                "com.nxp.mifare")){
            // Tag does not support Mifare Classic.
            return -2;
        } else {
            // Check if device does not support Mifare Classic.
            // For doing so, check if the ATQA + SAK of the tag indicate that
            // it's a Mifare Classic tag.
            // See: http://www.nxp.com/documents/application_note/AN10833.pdf
            // (Table 5 and 6)
            NfcA nfca = NfcA.get(tag);
            byte[] atqa = nfca.getAtqa();
            if (atqa[1] == 0 &&
                    (atqa[0] == 4 || atqa[0] == (byte)0x44 ||
                     atqa[0] == 2 || atqa[0] == (byte)0x42)) {
                // ATQA says it is most likely a Mifare Classic tag.
                byte sak = (byte)nfca.getSak();
                if (sak == 8 || sak == 9 || sak == (byte)0x18) {
                    // SAK says it is most likely a Mifare Classic tag.
                    // --> Device does not support Mifare Classic.
                    return -1;
                }
            }
            // Nope, it's not the device (most likely).
            // The tag does not support Mifare Classic.
            return -2;
        }
    }

    /**
     * Create a connected {@link MCReader} if there is a present Mifare Classic
     * tag. If there is no Mifare Classic tag a error
     * message will be displayed to toe user.
     * @param context The Context in which the error Toast will be shown.
     * @return A connected {@link MCReader} or "null" if no tag was present.
     */
    public static MCReader checkForTagAndCreateReader(Context context) {
        MCReader reader = null;
        boolean tagLost = false;
        // Check for tag.
        if (mTag != null && (reader = MCReader.get(mTag)) != null) {
            try {
                reader.connect();
            } catch (Exception e) {
                tagLost = true;
            }
            if (!tagLost && !reader.isConnected()) {
                reader.close();
                tagLost = true;
            }
            if (!tagLost) {
                return reader;
            }
        } else {
            tagLost = true;
        }

        // Error. The tag is gone.
        Toast.makeText(context, R.string.info_no_tag_found,
                Toast.LENGTH_LONG).show();
        return null;
    }

    /**
     * Depending on the provided Access Conditions this method will return
     * with which key you can achieve the operation ({@link Operations})
     * you asked for.<br />
     * This method contains the table from the NXP Mifare Classic Datasheet.
     * @param c1 Access Condition byte "C!".
     * @param c2 Access Condition byte "C2".
     * @param c3 Access Condition byte "C3".
     * @param op The operation you want to do.
     * @param isSectorTrailer True if it is a Sector Trailer, False otherwise.
     * @param isKeyBReadable True if key B is readable, False otherwise.
     * @return The operation "op" is possible with:<br />
     * <ul>
     * <li>0 - Never.</li>
     * <li>1 - Key A.</li>
     * <li>2 - Key B.</li>
     * <li>3 - Key A or B.</li>
     * <li>-1 - Error.</li>
     * </ul>
     */
    public static int getOperationInfoForBlock(byte c1, byte c2, byte c3,
            Operations op, boolean isSectorTrailer, boolean isKeyBReadable) {
        // Is Sector Trailer?
        if (isSectorTrailer) {
            // Sector Trailer.
            if (op != Operations.ReadKeyA && op != Operations.ReadKeyB
                    && op != Operations.ReadAC
                    && op != Operations.WriteKeyA
                    && op != Operations.WriteKeyB
                    && op != Operations.WriteAC) {
                // Error. Sector Trailer but no Sector Trailer permissions.
                return 4;
            }
            if          (c1 == 0 && c2 == 0 && c3 == 0) {
                if (op == Operations.WriteKeyA
                        || op == Operations.WriteKeyB
                        || op == Operations.ReadKeyB
                        || op == Operations.ReadAC) {
                    return 1;
                }
                return 0;
            } else if   (c1 == 0 && c2 == 1 && c3 == 0) {
                if (op == Operations.ReadKeyB
                        || op == Operations.ReadAC) {
                    return 1;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 0 && c3 == 0) {
                if (op == Operations.WriteKeyA
                        || op == Operations.WriteKeyB) {
                    return 2;
                }
                if (op == Operations.ReadAC) {
                    return 3;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 0) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                return 0;
            } else if   (c1 == 0 && c2 == 0 && c3 == 1) {
                if (op == Operations.ReadKeyA) {
                    return 0;
                }
                return 1;
            } else if   (c1 == 0 && c2 == 1 && c3 == 1) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                if (op == Operations.ReadKeyA
                        || op == Operations.ReadKeyB) {
                    return 0;
                }
                return 2;
            } else if   (c1 == 1 && c2 == 0 && c3 == 1) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                if (op == Operations.WriteAC) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 1) {
                if (op == Operations.ReadAC) {
                    return 3;
                }
                return 0;
            } else {
                return -1;
            }
        } else {
            // Data Block.
            if (op != Operations.Read && op != Operations.Write
                    && op != Operations.Increment
                    && op != Operations.DecTransRest) {
                // Error. Data block but no data block permissions.
                return -1;
            }
            if          (c1 == 0 && c2 == 0 && c3 == 0) {
                return (isKeyBReadable) ? 1 : 3;
            } else if   (c1 == 0 && c2 == 1 && c3 == 0) {
                if (op == Operations.Read) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 0 && c3 == 0) {
                if (op == Operations.Read) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                if (op == Operations.Write) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 0) {
                if (op == Operations.Read
                        || op == Operations.DecTransRest) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 2;
            } else if   (c1 == 0 && c2 == 0 && c3 == 1) {
                if (op == Operations.Read
                        || op == Operations.DecTransRest) {
                    return (isKeyBReadable) ? 1 : 3;
                }
                return 0;
            } else if   (c1 == 0 && c2 == 1 && c3 == 1) {
                if (op == Operations.Read || op == Operations.Write) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 0 && c3 == 1) {
                if (op == Operations.Read) {
                    return 2;
                }
                return 0;
            } else if   (c1 == 1 && c2 == 1 && c3 == 1) {
                return 0;
            } else {
                // Error.
                return -1;
            }
        }
    }

    /**
     * Check if key B is readable.
     * Key B is readable for the following configurations:
     * <ul>
     * <li>C1 = 0, C2 = 0, C3 = 0</li>
     * <li>C1 = 0, C2 = 0, C3 = 1</li>
     * <li>C1 = 0, C2 = 1, C3 = 0</li>
     * </ul>
     * @param c1 Access Condition byte "C1"
     * @param c2 Access Condition byte "C2"
     * @param c3 Access Condition byte "C3"
     * @return True if key B is readable. False otherwise.
     */
    public static boolean isKeyBReadable(byte c1, byte c2, byte c3) {
        if (c1 == 0
                && (c2 == 0 && c3 == 0)
                || (c2 == 1 && c3 == 0)
                || (c2 == 0 && c3 == 1)) {
            return true;
        }
        return false;
    }

    /**
     * Convert the Access Condition bytes to a matrix containing the
     * resolved C1, C2 and C3 for each block.
     * @param acBytes The Access Condition bytes (3 byte).
     * @return Matrix of access conditions bits (C1-C3) where the first
     * dimension is the "C" parameter (C1-C3, Index 0-2) and the second
     * dimension is the block number (Index 0-3). If the ACs are incorrect
     * null will be returned.
     */
    public static byte[][] acBytesToACMatrix(byte acBytes[]) {
        // ACs correct?
        // C1 (Byte 7, 4-7) == ~C1 (Byte 6, 0-3) and
        // C2 (Byte 8, 0-3) == ~C2 (Byte 6, 4-7) and
        // C3 (Byte 8, 4-7) == ~C3 (Byte 7, 0-3)
        byte[][] acMatrix = new byte[3][4];
        if (acBytes.length > 2 &&
                (byte)((acBytes[1]>>>4)&0x0F)  ==
                        (byte)((acBytes[0]^0xFF)&0x0F) &&
                (byte)(acBytes[2]&0x0F) ==
                        (byte)(((acBytes[0]^0xFF)>>>4)&0x0F) &&
                (byte)((acBytes[2]>>>4)&0x0F)  ==
                        (byte)((acBytes[1]^0xFF)&0x0F)) {
            // C1, Block 0-3
            for (int i = 0; i < 4; i++) {
                acMatrix[0][i] = (byte)((acBytes[1]>>>4+i)&0x01);
            }
            // C2, Block 0-3
            for (int i = 0; i < 4; i++) {
                acMatrix[1][i] = (byte)((acBytes[2]>>>i)&0x01);
            }
            // C3, Block 0-3
            for (int i = 0; i < 4; i++) {
                acMatrix[2][i] = (byte)((acBytes[2]>>>4+i)&0x01);
            }
            return acMatrix;
        }
        return null;
    }

    /**
     * Convert a matrix with Access Conditions bits into normal 3
     * Access Condition bytes.
     * @param acMatrix Matrix of access conditions bits (C1-C3) where the first
     * dimension is the "C" parameter (C1-C3, Index 0-2) and the second
     * dimension is the block number (Index 0-3).
     * @return The Access Condition bytes (3 byte).
     */
    public static byte[] acMatrixToACBytes(byte acMatrix[][]) {
        if (acMatrix != null && acMatrix.length == 3) {
            for (int i = 0; i < 3; i++) {
                if (acMatrix[i].length != 4)
                    // Error.
                    return null;
            }
        } else {
            // Error.
            return null;
        }
        byte[] acBytes = new byte[3];
        // Byte 6, Bit 0-3.
        acBytes[0] = (byte)((acMatrix[0][0]^0xFF)&0x01);
        acBytes[0] |= (byte)(((acMatrix[0][1]^0xFF)<<1)&0x02);
        acBytes[0] |= (byte)(((acMatrix[0][2]^0xFF)<<2)&0x04);
        acBytes[0] |= (byte)(((acMatrix[0][3]^0xFF)<<3)&0x08);
        // Byte 6, Bit 4-7.
        acBytes[0] |= (byte)(((acMatrix[1][0]^0xFF)<<4)&0x10);
        acBytes[0] |= (byte)(((acMatrix[1][1]^0xFF)<<5)&0x20);
        acBytes[0] |= (byte)(((acMatrix[1][2]^0xFF)<<6)&0x40);
        acBytes[0] |= (byte)(((acMatrix[1][3]^0xFF)<<7)&0x80);
        // Byte 7, Bit 0-3.
        acBytes[1] = (byte)((acMatrix[2][0]^0xFF)&0x01);
        acBytes[1] |= (byte)(((acMatrix[2][1]^0xFF)<<1)&0x02);
        acBytes[1] |= (byte)(((acMatrix[2][2]^0xFF)<<2)&0x04);
        acBytes[1] |= (byte)(((acMatrix[2][3]^0xFF)<<3)&0x08);
        // Byte 7, Bit 4-7.
        acBytes[1] |= (byte)((acMatrix[0][0]<<4)&0x10);
        acBytes[1] |= (byte)((acMatrix[0][1]<<5)&0x20);
        acBytes[1] |= (byte)((acMatrix[0][2]<<6)&0x40);
        acBytes[1] |= (byte)((acMatrix[0][3]<<7)&0x80);
        // Byte 8, Bit 0-3.
        acBytes[2] = (byte)(acMatrix[1][0]&0x01);
        acBytes[2] |= (byte)((acMatrix[1][1]<<1)&0x02);
        acBytes[2] |= (byte)((acMatrix[1][2]<<2)&0x04);
        acBytes[2] |= (byte)((acMatrix[1][3]<<3)&0x08);
        // Byte 8, Bit 4-7.
        acBytes[2] |= (byte)((acMatrix[2][0]<<4)&0x10);
        acBytes[2] |= (byte)((acMatrix[2][1]<<5)&0x20);
        acBytes[2] |= (byte)((acMatrix[2][2]<<6)&0x40);
        acBytes[2] |= (byte)((acMatrix[2][3]<<7)&0x80);

        return acBytes;
    }

    /**
     * Check if a (hex) string is pure hex (0-9, A-F, a-f) and 16 byte
     * (32 chars) long. If not show an error Toast in the context.
     * @param hexString The string to check.
     * @param context The Context in which the Toast will be shown.
     * @return True if sting is hex an 16 Bytes long, False otherwise.
     */
    public static boolean isHexAnd16Byte(String hexString, Context context) {
        if (hexString.matches("[0-9A-Fa-f]+") == false) {
            // Error, not hex.
            Toast.makeText(context, R.string.info_not_hex_data,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (hexString.length() != 32) {
            // Error, not 16 byte (32 chars).
            Toast.makeText(context, R.string.info_not_16_byte,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    /**
     * Check if the given block (hex string) is a value block.
     * NXP has PDFs describing what value blocks are. Google something
     * like "nxp mifare classic value block" if you want to have a
     * closer look.
     * @param hexString Block data as hex string.
     * @return True if it is a value block. False otherwise.
     */
    public static boolean isValueBlock(String hexString) {
        byte[] b = Common.hexStringToByteArray(hexString);
        if (b.length == 16) {
            // Google some NXP info PDFs about Mifare Classic to see how
            // Value Blocks are formated.
            // For better reading (~ = invert operator):
            // if (b0=b8 and b0=~b4) and (b1=b9 and b9=~b5) ...
            // ... and (b12=b14 and b13=b15 and b12=~b13) then
            if (    (b[0] == b[8] && (byte)(b[0]^0xFF) == b[4]) &&
                    (b[1] == b[9] && (byte)(b[1]^0xFF) == b[5]) &&
                    (b[2] == b[10] && (byte)(b[2]^0xFF) == b[6]) &&
                    (b[3] == b[11] && (byte)(b[3]^0xFF) == b[7]) &&
                    (b[12] == b[14] && b[13] == b[15] &&
                    (byte)(b[12]^0xFF) == b[13])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reverse a byte Array (e.g. Little Endian -> Big Endian).
     * Hmpf! Java has no Array.reverse(). And I don't want to use
     * Commons.Lang (ArrayUtils) form Apache....
     * @param array The array to reverse (in-place).
     */
    public static void reverseByteArrayInPlace(byte[] array) {
        for(int i = 0; i < array.length / 2; i++) {
            byte temp = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = temp;
        }
    }

    /**
     * Convert an array of bytes into a string of hex values.
     * @param bytes Bytes to convert.
     * @return The bytes in hex string format.
     */
    public static String byte2HexString(byte[] bytes) {
        String ret = "";
        if (bytes != null) {
            for (Byte b : bytes) {
                ret += String.format("%02X", b.intValue() & 0xFF);
            }
        }
        return ret;
    }

    /**
     * Convert a string of hex data into a byte array.
     * Original author is: Dave L. (http://stackoverflow.com/a/140861).
     * @param s The hex string to convert
     * @return An array of bytes with the values of the string.
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                     + Character.digit(s.charAt(i+1), 16));
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, "Argument(s) for hexStringToByteArray(String s)"
                    + "was not a hex string");
        }
        return data;
    }

    /**
     * Create a colored string.
     * @param data The text to be colored.
     * @param color The color for the text.
     * @return A colored string.
     */
    public static SpannableString colorString(String data, int color) {
        SpannableString ret = new SpannableString(data);
        ret.setSpan(new ForegroundColorSpan(color),
                0, data.length(), 0);
        return ret;
    }

    /**
     * Copy a text to the Android clipboard.
     * @param text The text that should be stored on the clipboard.
     * @param context Context of the SystemService
     * (and the Toast message that will by shown).
     */
    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    public static void copyToClipboard(String text, Context context) {
        if (text.equals("") == false) {
            if (Build.VERSION.SDK_INT >= 11) {
                // Android API level 11+.
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager)
                        context.getSystemService(
                                Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip =
                        android.content.ClipData.newPlainText(
                                "mifare classic tool data", text);
                clipboard.setPrimaryClip(clip);
            } else {
                // Android API level 10.
                android.text.ClipboardManager clipboard =
                        (android.text.ClipboardManager)
                        context.getSystemService(
                                Context.CLIPBOARD_SERVICE);
                clipboard.setText(text);
            }
            Toast.makeText(context, R.string.info_copied_to_clipboard,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Get the content of the Android clipboard (if it is plain text).
     * @param context Context of the SystemService
     * @return The content of the Android clipboard. On error
     * (clipboard empty, clipboard content not plain text, etc.) null will
     * be returned.
     */
    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    public static String getFromClipboard(Context context) {
        if (Build.VERSION.SDK_INT >= 11) {
            // Android API level 11+.
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager)
                    context.getSystemService(
                            Context.CLIPBOARD_SERVICE);
            if (clipboard.getPrimaryClip() != null
                    && clipboard.getPrimaryClip().getItemCount() > 0
                    && clipboard.getPrimaryClipDescription().hasMimeType(
                        android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                return clipboard.getPrimaryClip()
                        .getItemAt(0).getText().toString();
            }
        } else {
            // Android API level 10.
            android.text.ClipboardManager clipboard =
                    (android.text.ClipboardManager)
                    context.getSystemService(
                            Context.CLIPBOARD_SERVICE);
            if (clipboard.hasText()) {
                return clipboard.getText().toString();
            }
        }

        // Error.
        return null;
    }

    /**
     * Copy file.
     * @param in Input file (source).
     * @param out Output file (destination).
     * @throws IOException
     */
    public static void copyFile(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
          out.write(buffer, 0, read);
        }
    }

    /**
     * Get the current active (last detected) Tag.
     * @return The current active Tag.
     * @see #mTag
     */
    public static Tag getTag() {
        return mTag;
    }

    /**
     * Set the new active Tag (and update {@link #mUID}).
     * @param tag The new Tag.
     * @see #mTag
     * @see #mUID
     */
    public static void setTag(Tag tag) {
        mTag = tag;
        mUID = tag.getId();
    }

    /**
     * Get the App wide used NFC adapter.
     * @return NFC adapter.
     */
    public static NfcAdapter getNfcAdapter() {
        return mNfcAdapter;
    }

    /**
     * Set the App wide used NFC adapter.
     * @param nfcAdapter The NFC adapter that should be used.
     */
    public static void setNfcAdapter(NfcAdapter nfcAdapter) {
        mNfcAdapter = nfcAdapter;
    }

    /**
     * Get the key map generated by {@link CreateKeyMap.CreateKeyMap}.
     * @return A key map (see {@link MCReader#getKeyMap()}).
     */
    public static SparseArray<byte[][]> getKeyMap() {
        return mKeyMap;
    }

    /**
     * Set {@link #mKeyMapFrom} and {@link #mKeyMapTo}.
     * The  {@link CreateKeyMap.CreateKeyMap} will do this for every
     * created key map.
     * @param from {@link #mKeyMapFrom}
     * @param to {@link #mKeyMapTo}
     */
    public static void setKeyMapRange (int from, int to){
        mKeyMapFrom = from;
        mKeyMapTo = to;
    }

    /**
     * Get the key map start point.
     * @return {@link #mKeyMapFrom}
     */
    public static int getKeyMapRangeFrom() {
        return mKeyMapFrom;
    }

    /**
     * Get the key map end point
     * @return {@link #mKeyMapTo}
     */
    public static int getKeyMapRangeTo() {
        return mKeyMapTo;
    }

    /**
     * Set the key map.
     * @param value A key map (see {@link MCReader#getKeyMap()}).
     */
    public static void setKeyMap(SparseArray<byte[][]> value) {
        mKeyMap = value;
    }

    /**
     * Get the UID of the current tag.
     * @return The UID of the current tag.
     * @see #mUID
     */
    public static byte[] getUID() {
        return mUID;
    }

    /**
     * Get the version code.
     * @return The version code.
     */
    public static String getVersionCode() {
        return mVersionCode;
    }
}
