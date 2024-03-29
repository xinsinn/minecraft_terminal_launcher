package top.xinsin.minecraft;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import top.xinsin.entity.XMTLEntity;
import top.xinsin.enums.VersionType;
import top.xinsin.util.FileUtil;
import top.xinsin.util.InputUtil;
import top.xinsin.util.StringConstant;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created On 8/3/22 1:07 PM
 *
 * @author xinsin
 * @version 1.0.0
 */
@Slf4j
public class LaunchMinecraft {
    private File file = null;

    public void readVersionJson(String path){
        file = new File(path);
        String content = null;
        content = FileUtil.readFile(path);
        JSONObject jsonObject = JSONObject.parseObject(content);
        HashSet<String> libraries = null;
        if (jsonObject.getJSONArray("patches") == null){
            libraries = getVillagerClassPath(jsonObject.getJSONArray("libraries"),VersionType.villager);
        }else{
            libraries = getVillagerClassPath(jsonObject.getJSONArray("patches"),VersionType.fabric);
        }

        Map<String, String> jvmArgs = getJvmArgs(jsonObject.getJSONObject("arguments").getJSONArray("jvm"));
        Map<String, String> minecraftArgs = getMinecraftArgs(jsonObject);
        StringBuilder shellText = new StringBuilder();
        XMTLEntity xmtlEntity = FileUtil.readConfigureFile();
        shellText.append("#!/usr/bin/env bash")
                .append("\n")
                .append("cd ")
                .append(file.getPath(), 0, file.getPath().lastIndexOf(File.separator))
                .append("\n")
                .append(xmtlEntity.getWrapCommand())
                .append(" ")
                .append(xmtlEntity.getSelectJavaVersion())
                .append(" ");
        for (Map.Entry<String,String> entry:jvmArgs.entrySet()) {
            if (entry.getValue().equals("@")) {
                shellText.append(entry.getKey()).append(" ");
            } else {
                if (entry.getKey().equals("-Djava.library.path")) {
                    String i = file.getPath();
                    String substring = i.substring(0, i.lastIndexOf("/"));
                    for (String s : Objects.requireNonNull(new File(substring).list())) {
                        if (s.startsWith("natives-linux") || s.endsWith("natives")) {
                            shellText.append(entry.getKey()).append("=").append(substring).append(File.separator).append(s).append(" ");
                        }
                    }
                }
                if (entry.getKey().equals("-Dminecraft.launcher.brand")) {
                    shellText.append(entry.getKey()).append("=").append(StringConstant.BRAND).append(" ");
                }
                if (entry.getKey().equals("-Dminecraft.launcher.version")) {
                    shellText.append(entry.getKey()).append("=").append(StringConstant.VERSION).append(" ");
                }
                if (entry.getKey().equals("-Dminecraft.client.jar")) {
                    String i = file.getPath();
                    String substring = i.substring(0, i.lastIndexOf("/"));
                    for (String s : Objects.requireNonNull(new File(substring).list())) {
                        if (s.endsWith(".jar")) {
                            shellText.append(entry.getKey()).append("=").append(substring).append(File.separator).append(s).append(" ");
                        }
                    }
                }
                if (entry.getKey().equals("-cp")) {
                    shellText.append(entry.getKey()).append(" ");
                    //        拼接mc源码的jar路径
                    String substring = file.getPath().substring(0, file.getPath().lastIndexOf(File.separator));
                    File file1 = new File(substring);
                    for (File file2 : Objects.requireNonNull(file1.listFiles())) {
                        String path1 = file2.getPath();
                        if (path1.endsWith("jar")){
                            shellText.append(path1).append(":");
                        }
                    }
                    AtomicInteger num = new AtomicInteger();
                    HashSet<String> finalLibraries = libraries;
                    libraries.forEach(e ->{
                        shellText.append(InputUtil.minecraft_libraries).append(e);
                        if (num.get() != finalLibraries.size() - 1) {
                            shellText.append(File.pathSeparator);
                        }
                        num.getAndIncrement();
                    });
                    shellText.append(" ");
                }
            }
        }
        for (Map.Entry<String,String> entry:minecraftArgs.entrySet()) {
            if (entry.getKey().equals("mainClass")){
                shellText.append(entry.getValue()).append(" ");
            }
        }
        for (Map.Entry<String,String> entry:minecraftArgs.entrySet()) {
            if (!entry.getKey().equals("mainClass")){
                shellText.append(entry.getKey()).append(" ").append(entry.getValue()).append(" ");
            }
        }
        try {
            FileUtils.writeStringToFile(new File("launch.sh"), shellText.toString(), "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            String property = System.getProperty("user.dir");
            Process exec = Runtime.getRuntime().exec("sh -c ./launch.sh", null, new File(property));
            BufferedReader input = new BufferedReader(new InputStreamReader(exec.getInputStream()));
            String line = "";
            while ((line = input.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println(shellText.toString());
        log.info("结束minecraft进程");
    }

    private Map<String,String> getMinecraftArgs(JSONObject jsonObject){
        XMTLEntity XMTLEntity = updatePlayerInfo();
        Map<String,String> minecraftArgs = new HashMap<>();
        String mainClass = jsonObject.getString("mainClass");
        String version = jsonObject.getString("id");
        String assetIndex = jsonObject.getJSONObject("assetIndex").getString("id");
        minecraftArgs.put("mainClass",mainClass);
        minecraftArgs.put("--username", XMTLEntity.getName());
        minecraftArgs.put("--version",version);
        minecraftArgs.put("--assetIndex",assetIndex);
        minecraftArgs.put("--uuid", XMTLEntity.getUuid());
        minecraftArgs.put("--accessToken", XMTLEntity.getAccessToken());
        minecraftArgs.put("--versionType","\"" + StringConstant.LAUNCH_VERSION + "\"");
        minecraftArgs.put("--width",StringConstant.WIDTH.toString());
        minecraftArgs.put("--height",StringConstant.HEIGHT.toString());
        minecraftArgs.put("--gameDir",file.getPath().substring(0,file.getPath().lastIndexOf(File.separator)));
        minecraftArgs.put("--assetsDir",InputUtil.minecraftPath + "assets");
        return minecraftArgs;
    }
    /**
     * 获取jvm启动信息
     * @param jsonArray
     * @return
     */
    private Map<String,String> getJvmArgs(JSONArray jsonArray){
        Map<String,String> jvmArgs = new HashMap<>();
        ArrayList<String> content = new ArrayList<>();
//        第一遍遍历用于删除掉不需要的参数
        for (int i = 0; i < jsonArray.size(); i++) {
            String string = jsonArray.getString(i);
            if(string.startsWith("-") || string.startsWith("$")){
                content.add(string);
            }
        }
        content.forEach(e ->{
            jsonArray.remove(e);
            if (!e.equals("${classpath}")){
                String[] split = e.split("=");
                if (split.length == 2){
                    jvmArgs.put(split[0],split[1]);
                }else {
                    jvmArgs.put(split[0],"");
                }
            }
        });
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONArray rules = jsonArray.getJSONObject(i).getJSONArray("rules");
            String action = rules.getJSONObject(0).getString("action");
            if(action.equals("allow")){
                String os = rules.getJSONObject(0).getJSONObject("os").getString("name");
                if(StringConstant.OS_NAME.equals(os)){
                    JSONArray value = jsonArray.getJSONObject(i).getJSONArray("value");
                    for (int j = 0; j < value.size(); j++) {
                        String[] split = value.getString(j).split("=");
                        jvmArgs.put(split[0],split[1]);
                    }
                }
            }
        }
        jvmArgs.put("-Dfile.encoding","UTF-8");
        jvmArgs.put("-Dminecraft.client.jar","");
        jvmArgs.put("-Dfml.ignoreInvalidMinecraftCertificates","true");
        jvmArgs.put("-Dfml.ignorePatchDiscrepancies","true");
        jvmArgs.put("-Dlog4j.configurationFile",file.getPath().substring(0,file.getPath().lastIndexOf(File.separator)) + "/log4j2.xml");
        jvmArgs.put("-Xmn128m","@");
        jvmArgs.put("-Xmx1024m","@");
        jvmArgs.put("-XX:+UseG1GC","@");
        jvmArgs.put("-XX:+UnlockExperimentalVMOptions","@");
        jvmArgs.put("-XX:-UseAdaptiveSizePolicy","@");
        jvmArgs.put("-XX:-OmitStackTraceInFastThrow","@");
        return jvmArgs;
    }

    /**
     * 获取类路径
     * @param jsonArray
     * @return
     */
    private HashSet<String> getFabricClassPath(JSONArray jsonArray,HashSet<String> hashSet){
        for (int i = 0; i < jsonArray.size(); i++) {
            String[] names = getLibraries(jsonArray.getJSONObject(i).getString("name"));
            String fabricClasspath = getFabricClasspath(names);
            hashSet.add(fabricClasspath);
        }
        return hashSet;
    }
    private HashSet<String > villagerClassPath(JSONArray libraries){
        HashSet<String> classPath = new HashSet<>();
        for (int i = 0; i < libraries.size(); i++) {
            JSONObject downloads = libraries.getJSONObject(i).getJSONObject("downloads");
            String artifact = null;
            if (downloads != null) {
                artifact = downloads.getJSONObject("artifact").getString("path");
                JSONArray rules = libraries.getJSONObject(i).getJSONArray("rules");
                String action = null;
                if (rules != null && rules.size() == 1) {
                    action = rules.getJSONObject(0).getString("action");
                    JSONObject os = rules.getJSONObject(0).getJSONObject("os");
                    if (os != null) {
                        String os_name = os.getString("name");
                        if (action.equals(StringConstant.ALLOW)) {
                            if (os_name.equals(StringConstant.OS_NAME)) {
                                classPath.add(artifact);
                            } else {
                                continue;
                            }
                        }
                    }
                } else if (rules != null && rules.size() == 2) {
                    action = rules.getJSONObject(0).getString("action");
                    if (action.equals(StringConstant.ALLOW)) {
                        String disAllow = rules.getJSONObject(1).getString("action");
                        JSONObject os = rules.getJSONObject(1).getJSONObject("os");
                        String os_name = os.getString("name");
                        if (disAllow.equals(StringConstant.DISALLOW)) {
                            if (!os_name.equals(StringConstant.OS_NAME)) {
                                classPath.add(artifact);
                            } else {
                                classPath.add(artifact);
                            }
                        }
                    }
                }
            }else {
                if (libraries.getJSONObject(i).getJSONObject("downloads") == null) {
                    String name1 = libraries.getJSONObject(i).getString("name");
                    String[] names = getLibraries(name1);
                    String fabricClasspath = getFabricClasspath(names);
                    classPath.add(fabricClasspath);
                }
            }
            if (artifact != null) {
                classPath.add(artifact);
            }
        }
        return classPath;
    }
    private String getFabricClasspath(String[] names){
        String packages = names[0];
        String name = names[1];
        String version = names[2];
        StringBuilder path = new StringBuilder();
        String[] packages1 = packages.split("\\.");
        for (String s : packages1) {
            path.append(s);
            path.append(File.separator);
        }
        path.append(name)
                .append(File.separator)
                .append(version)
                .append(File.separator)
                .append(name)
                .append("-")
                .append(version)
                .append(".jar");
        return path.toString();
    }
    private HashSet<String> getVillagerClassPath(JSONArray libraries, VersionType versionType){
        HashSet<String> classPath = null;
        //拼接原版依赖库jar路径
        if (versionType == VersionType.villager) {
            classPath = villagerClassPath(libraries);
        }else if (versionType == VersionType.fabric){
            classPath = villagerClassPath(libraries.getJSONObject(0).getJSONArray("libraries"));
            //拼接fabric依赖库jar路径
            getFabricClassPath(libraries.getJSONObject(1).getJSONArray("libraries"),classPath);
        }
        return classPath;
    }

    /**
     * 将fabric的库名转换成分割字符串
     * 用来重新拼接文件路径
     * @param name
     * @return
     */
    private String[] getLibraries(String name){
        return name.split(":");
    }

    private XMTLEntity updatePlayerInfo(){
        new MicrosoftLogin().accountRefresh();
        return JSONObject.parseObject(FileUtil.readFile(StringConstant.XMTL_INFO_PATH), XMTLEntity.class);
    }
}
