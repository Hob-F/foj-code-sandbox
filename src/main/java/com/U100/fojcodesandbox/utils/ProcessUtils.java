package com.U100.fojcodesandbox.utils;

import cn.hutool.core.util.StrUtil;
import com.U100.fojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/**
 * 进程工具类
 */
public class ProcessUtils {

    /**
     * 执行进程并获取信息
     *
     * @param runProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            // 等待程序执行期间,轮询 /proc/{pid}/status 采集 VmRSS/VmHWM 物理内存峰值
            long peakMemoryKb = -1;
            while (!runProcess.waitFor(10, TimeUnit.MILLISECONDS)) {
                long memoryKb = getProcessMemoryKb(runProcess);
                if (memoryKb > 0) {
                    peakMemoryKb = Math.max(peakMemoryKb, memoryKb);
                }
            }
            int exitValue = runProcess.exitValue();
            executeMessage.setExitValue(exitValue);
            // 正常退出
            if (exitValue == 0) {
                System.out.println(opName + "成功");
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());
            } else {
                // 异常退出
                System.out.println(opName + "失败，错误码： " + exitValue);
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());

                // 分批获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                StringBuilder errorCompileOutputStringBuilder = new StringBuilder();

                // 逐行读取
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorCompileOutputStringBuilder.append(errorCompileOutputLine).append("\n");
                }
                executeMessage.setErrorMessage(errorCompileOutputStringBuilder.toString());
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
            // 采到内存才设置;采不到(进程退出过快/非 Linux)时保持 null,而不是 0
            if (peakMemoryKb > 0) {
                executeMessage.setMemory(peakMemoryKb);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }

    /**
     * 读取子进程物理内存(KB)。
     *
     * <p>通过 /proc/{pid}/status 的 VmRSS(当前物理内存)实测;若 VmRSS 采不到,
     * 用 VmHWM(历史峰值)兜底;两者都拿不到返回 -1(由调用方标 null)。</p>
     *
     * @param process 子进程
     * @return 内存占用(KB),-1 表示采集失败
     */
    private static long getProcessMemoryKb(Process process) {
        try {
            long pid = getProcessPid(process);
            if (pid <= 0) {
                return -1;
            }
            File statusFile = new File("/proc/" + pid + "/status");
            if (!statusFile.exists()) {
                return -1;
            }
            long vmRss = -1;
            long vmHwm = -1;
            BufferedReader reader = new BufferedReader(new FileReader(statusFile));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("VmRSS:")) {
                        vmRss = parseKb(line);
                    } else if (line.startsWith("VmHWM:")) {
                        vmHwm = parseKb(line);
                    }
                }
            } finally {
                reader.close();
            }
            return vmRss > 0 ? vmRss : (vmHwm > 0 ? vmHwm : -1);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 获取子进程 PID。
     *
     * <p>JDK 8 无 Process.pid()(Java 9+ 才有),用反射读取 UNIXProcess 的 pid 字段;
     * 非 Linux(如 Windows)或反射失败返回 -1。</p>
     *
     * @param process 子进程
     * @return PID,-1 表示取不到
     */
    private static long getProcessPid(Process process) {
        try {
            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            return pidField.getLong(process);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 解析 /proc/{pid}/status 中形如 "VmRSS:      1234 kB" 的值为 KB 数值。
     *
     * @param line 状态文件中的一行
     * @return KB 数值,解析失败返回 -1
     */
    private static long parseKb(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                return Long.parseLong(parts[1]);
            } catch (NumberFormatException ignored) {
                // 解析失败忽略
            }
        }
        return -1;
    }

    /**
     * 执行交互式进程并获取信息
     *
     * @param runProcess
     * @param args
     * @return
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            // 向控制台输入程序
            OutputStream outputStream = runProcess.getOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] s = args.split(" ");
            String join = StrUtil.join("\n", s) + "\n";
            outputStreamWriter.write(join);
            // 相当于按了回车，执行输入的发送
            outputStreamWriter.flush();

            // 分批获取进程的正常输出
            InputStream inputStream = runProcess.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder compileOutputStringBuilder = new StringBuilder();
            // 逐行读取
            String compileOutputLine;
            while ((compileOutputLine = bufferedReader.readLine()) != null) {
                compileOutputStringBuilder.append(compileOutputLine);
            }
            executeMessage.setMessage(compileOutputStringBuilder.toString());
            // 记得资源的释放，否则会卡死
            outputStreamWriter.close();
            outputStream.close();
            inputStream.close();
            runProcess.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }
}
