package com.github.cwdtom.gateway;

import com.github.cwdtom.gateway.constant.Constant;
import com.github.cwdtom.gateway.environment.ApplicationContext;
import com.github.cwdtom.gateway.limit.TokenProvider;
import com.github.cwdtom.gateway.listener.HttpListener;
import com.github.cwdtom.gateway.listener.HttpsListener;
import com.github.cwdtom.gateway.mapping.SurvivalCheck;
import com.github.cwdtom.gateway.thread.DefaultRejectedExecutionHandler;
import com.github.cwdtom.gateway.thread.DefaultThreadFactory;
import com.github.cwdtom.gateway.thread.ThreadPoolGroup;
import eu.medsea.mimeutil.MimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 启动类
 *
 * @author chenweidong
 * @since 1.0.0
 */
@Slf4j
public class Application {
    /**
     * 启动方法
     *
     * @param args 参数
     */
    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption(Constant.COMMAND_CONFIG, true, "config file path");
        options.addOption(Constant.COMMAND_HELP, false, "help info");
        options.addOption(Constant.COMMAND_VERSION, false, "show version info");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        if (cmd.hasOption(Constant.COMMAND_HELP)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("ant", options);
            System.exit(0);
        } else if (cmd.hasOption(Constant.COMMAND_VERSION)) {
            System.out.println("version: " + Constant.VERSION);
            System.exit(0);
        }

        ApplicationContext ac = null;
        if (cmd.hasOption(Constant.COMMAND_CONFIG)) {
            // 初始化上下文
            ac = new ApplicationContext(cmd.getOptionValue(Constant.COMMAND_CONFIG));
        } else {
            log.error("config file path arg is not found.");
            System.exit(1);
        }

        // 初始化服务线程池
        ThreadPoolExecutor serviceThreadPool = new ThreadPoolExecutor(10, 10,
                2000, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10), new DefaultThreadFactory("service"),
                new DefaultRejectedExecutionHandler());
        // 加载mime资源
        MimeUtil.registerMimeDetector("eu.medsea.mimeutil.detector.MagicMimeMimeDetector");
        ThreadPoolGroup tpe = ac.getContext(ThreadPoolGroup.class);
        // 启动限流
        TokenProvider token = new TokenProvider(ac);
        serviceThreadPool.execute(token);
        // 开启生存检查
        SurvivalCheck check = new SurvivalCheck();
        serviceThreadPool.execute(check);
        // 启动http监听
        HttpListener http = new HttpListener(ac);
        serviceThreadPool.execute(http);
        // 启动https监听
        HttpsListener https = new HttpsListener(ac);
        serviceThreadPool.execute(https);

        // 添加销毁事件
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            http.shutdown();
            System.out.println("http listener was shutdown!");
            https.shutdown();
            System.out.println("https listener was shutdown!");
            token.shutdown();
            System.out.println("token provider was shutdown!");
            check.shutdown();
            System.out.println("survival check was shutdown!");
            tpe.shutdown();
            serviceThreadPool.shutdown();
            System.out.println("gateway was shutdown!");
        }));
    }
}
