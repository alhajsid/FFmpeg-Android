package nl.bravobit.ffmpeg;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.hzy.libp7zip.P7ZipApi;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;


@SuppressWarnings({"unused", "WeakerAccess"})
public class FFmpegArchiveUtil {


    public static final int VERSION = 17; // up this version when you add a new ffmpeg build
    public static final String KEY_PREF_VERSION = "ffmpeg_version";
    public static final String FFMPEG_ARCHIVE = "ffmpeg_arch.7z";
    public static final String FFMPEG_FILE_NAME = "ffmpeg";
    public static final String FFPROBE_FILE_NAME = "ffprobe";

    private static File getFFmpegArchive(Context context) {
        return new File(context.getFilesDir(), FFMPEG_ARCHIVE);
    }

    private static File getFFmpegFile(Context context) {
        return new File(context.getFilesDir(), FFMPEG_FILE_NAME);
    }

    private static File getFFprobe(Context context) {
        return new File(context.getFilesDir(), FFPROBE_FILE_NAME);
    }


    public interface FFmpegSupportCallback {
        void isFFmpegSupported(boolean isSupported);
    }


    private static class FFmpegArchiveCopyTask extends AsyncTask<Void, Void, Boolean> {
        InputStream stream;
        File ffmpegArchive;
        FFmpegSupportCallback callback;

        FFmpegArchiveCopyTask(InputStream stream, File file, FFmpegSupportCallback callback) {
            this.ffmpegArchive = file;
            this.stream = stream;
            this.callback = callback;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            File ffmpegFile = new File(FilenameUtils.getFullPath(ffmpegArchive.getAbsolutePath()) + FFMPEG_FILE_NAME);
            if (ffmpegFile.exists()) {
                return true;
            } else {
                if (ffmpegArchive.exists()) {
                    return true;
                } else {
                    try {
                        FileUtils.copyToFile(stream, ffmpegArchive);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            try {
                FFmpegExtractorAsyncTask fFmpegExtractorAsyncTask = new FFmpegExtractorAsyncTask(stream, ffmpegArchive, callback);
                fFmpegExtractorAsyncTask.execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    @SuppressWarnings("DanglingJavadoc")
    private static class FFmpegExtractorAsyncTask extends AsyncTask<Void, Void, Boolean> {


        InputStream stream;

        /**
         String archiveFormat =  {@link FFMPEG_ARCHIVE}
         */


        /**
         * The outPut path for copying archiveFormat
         * /data/user/0/com.symphonyrecords.mediacomp/files/
         */
        String bb;


        /**
         * FFmpeg archive file
         * /data/user/0/com.symphonyrecords.mediacomp/files/archiveFormat
         */
        File ffmpegArchive;


        /**
         * The main ffmpeg file
         * /data/user/0/com.symphonyrecords.mediacomp/files/ffmpeg
         */
        File ffmpegFile;

        FFmpegSupportCallback callback;

        FFmpegExtractorAsyncTask(InputStream stream, File file, FFmpegSupportCallback callback) {
            this.ffmpegArchive = file;
            this.stream = stream;
            this.callback = callback;
            bb = FilenameUtils.getFullPath(ffmpegArchive.getAbsolutePath());
            ffmpegFile = new File(bb + FFMPEG_FILE_NAME);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (ffmpegFile.exists()) {
                return true;
            } else {
                if (ffmpegArchive.exists()) {
                    try {
                        String cmd = extractCmd(ffmpegArchive.getAbsolutePath(), bb);
                        P7ZipApi.executeCommand(cmd);
                        return true;
                    } catch (Throwable e) {
                        e.printStackTrace();
                        return false;
                    }
                } else {
                    try {
                        FileUtils.copyToFile(stream, ffmpegArchive);
                    } catch (Exception ignored) {
                    }
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean isSuccess) {
            super.onPostExecute(isSuccess);
            if (isSuccess) {
                if (ffmpegFile.exists()) {
                    if (ffmpegArchive.exists()) {
                        deleteFile(ffmpegArchive.getAbsolutePath());
                    }
                    Log.d("onPostExecute", "ffmpegFile.exists()");
                    if (makeFileExecutable(ffmpegFile)) {
                        callback.isFFmpegSupported(true);
                        Log.d("onPostExecute", "makeFileExecutable Successful");
                    } else {
                        callback.isFFmpegSupported(false);
                        Log.d("onPostExecute", "makeFileExecutable Failed Again");
                    }
                } else {
                    callback.isFFmpegSupported(false);
                    Log.d("onPostExecute", "!ffmpegFile.exists()");
                }
                Log.d("onPostExecuteResult", "Successful");
            } else {
                Log.d("onPostExecute", "NotSuccessful");
                callback.isFFmpegSupported(false);
            }
        }
    }


    /**
     * Copying FFMPEG binary to application directory////////////
     */
    public static void initFFmpegBinary(Context context, FFmpegSupportCallback callback) {
        try {
            File f = getFFmpegFile(context);
            if (f.exists() && f.canExecute()) {
                callback.isFFmpegSupported(true);
            } else {
                extractFFMPEG(context, callback);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static void extractFFMPEG(Context context, FFmpegSupportCallback callback) {
        if (CpuArchHelper.cpuNotSupported()) {
            callback.isFFmpegSupported(false);
            return;
        }
        // Copy Archive To App Dir
        SharedPreferences settings = context.getSharedPreferences("ffmpeg_prefs", Context.MODE_PRIVATE);
        int version = settings.getInt(KEY_PREF_VERSION, 0);

        // check if ffmpeg file exists
        if (getFFmpegFile(context).exists() && version >= VERSION) {
            callback.isFFmpegSupported(true);
        } else {
            try {
                Log.d("extractFFMPEG", "FFmpeg Binary does not exist, initializing copy process...");
                InputStream stream = context.getAssets().open(FFMPEG_ARCHIVE);
                FFmpegArchiveCopyTask fFmpegArchiveCopyTask = new FFmpegArchiveCopyTask(stream, getFFmpegArchive(context), callback);
                fFmpegArchiveCopyTask.execute();
                settings.edit().putInt(KEY_PREF_VERSION, VERSION).apply();
            } catch (Exception e) {
                Log.e("extractFFMPEG", "error while opening assets", e);
                callback.isFFmpegSupported(false);
            }
        }
    }

    private static boolean makeFileExecutable(File file) {
        if (!file.canExecute()) {
            // try to make executable
            try {
                try {
                    Runtime.getRuntime().exec("chmod -R 777 " + file.getAbsolutePath()).waitFor();
                } catch (InterruptedException e) {
                    Log.e("makeFileExecutable", "interrupted exception", e);
                    return false;
                } catch (IOException e) {
                    Log.e("makeFileExecutable", "io exception", e);
                    return false;
                }
                if (!file.canExecute()) {
                    if (!file.setExecutable(true)) {
                        Log.e("makeFileExecutable", "unable to make executable");
                        return false;
                    }
                }
            } catch (SecurityException e) {
                Log.e("makeFileExecutable", "security exception", e);
                return false;
            }
        }
        return file.canExecute();
    }

    private static void deleteFile(String fileName) {
        try {
            File file = new File(fileName);
            if (FileUtils.deleteQuietly(file))
                System.out.println(file.getName() + " is deleted!");
            else {
                if (file.delete()) {
                    System.out.println(file.getName() + " is deleted!");
                } else {
                    try {
                        FileUtils.forceDelete(file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createPath(File path) {
        try {
            FileUtils.forceMkdir(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static final String P7Z = "7z";
    /**
     * Command	Description
     * a	Add
     * b	Benchmark
     * d	Delete
     * e	Extract
     * h	Hash
     * i	Show information about supported formats
     * l	List
     * rn	Rename
     * t	Test
     * u	Update
     * x	eXtract with full paths
     */
    private static final String CMD_ADD = "a";
    private static final String CMD_BENCHMARK = "b";
    private static final String CMD_DELETE = "d";
    private static final String CMD_EXTRACT = "e";
    private static final String CMD_HASH = "h";
    private static final String CMD_INFO = "i";
    private static final String CMD_LIST = "l";
    private static final String CMD_RENAME = "rn";
    private static final String CMD_TEST = "t";
    private static final String CMD_UPDATE = "u";
    private static final String CMD_EXTRACT1 = "x";

    /**
     * Switch	Description
     * -i	Include filenames
     * -m	Set Compression Method
     * -o	Set Output directory
     * -p	Set Password
     * -t	Type of archive
     * -u	Update options
     * -x	Exclude filenames
     * -y	Assume Yes on all queries
     */
    private static final String SWH_INCLUDE = "-i";
    private static final String SWH_METHOD = "-m";
    private static final String SWH_OUTPUT = "-o";
    private static final String SWH_PASSWORD = "-p";
    private static final String SWH_TYPE = "-t";
    private static final String SWH_UPDATE = "-u";
    private static final String SWH_EXCLUDE = "-x";
    private static final String SWH_YES = "-y";

    private static String compressCmd(String filePath, String outPath, String type) {
        return String.format("7z a -t%s '%s' '%s'", type, outPath, filePath);
    }

    private static String extractCmd(String archivePath, String outPath) {
        return String.format("%s %s '%s' '%s%s' '%s' -aoa", P7Z, CMD_EXTRACT, archivePath, SWH_OUTPUT, outPath, CpuArchHelper.getCpuArchiveFolder());
    }
    //    public static String extractCmd(String archivePath, String outPath) {
    //        return String.format("%s %s '%s' '%s%s' -aoa", P7Z, CMD_EXTRACT1,archivePath, SWH_OUTPUT, outPath);
    //    }


    private static class CpuArchHelper {
        //// ---------  x86 Cpu ABI ----------- ////
        private static final String X86_CPU = "x86";
        private static final String X86_64_CPU = "x86_64";

        //// ---------  ARM Cpu ABI ----------- ////
        private static final String ARM_ABI = "armeabi";
        private static final String ARM_V7_CPU = "armeabi-v7a";
        private static final String ARM_64_CPU = "arm64-v8a";

        //// ---------  MIPS Cpu ABI ----------- ////
        private static final String MIPS_ABI = "mips";
        private static final String MIPS64_ABI = "mips64";


        public enum CpuArch {
            ARMv7, x86, NONE
        }


        public static CpuArch getCpuArch() {
            String cpu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? Build.SUPPORTED_ABIS[0] : Build.CPU_ABI;
            Log.d("Device_Cpu: ", cpu);
            switch (cpu) {
                case X86_CPU:
                case X86_64_CPU:
                    return CpuArch.x86;

                case ARM_ABI:
                case ARM_V7_CPU:
                case ARM_64_CPU:
                    return CpuArch.ARMv7;

                case MIPS_ABI:
                case MIPS64_ABI:
                    return CpuArch.NONE;

                default:
                    return CpuArch.NONE;
            }
        }

        public static boolean cpuNotSupported() {
            return getCpuArch() == CpuArch.NONE;
        }

        public static String getCpuArchiveFolder() {
            switch (getCpuArch()) {
                case ARMv7:
                    return "arm";
                case x86:
                    return "x86";
                case NONE:
                    return "arm";
                default:
                    return "arm";
            }
        }

    }


}