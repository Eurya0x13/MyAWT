//
// Created by qz919 on 2025/10/3.
//

#ifndef ANDROID_LOGGER_HPP
#define ANDROID_LOGGER_HPP

#include <android/log.h>
#include <string>
#include <format>
#include <sstream>

constexpr auto LOG_TAG = "NativeJavaLauncher";

enum class LogType : uint8_t {
    DEBUG,      // 调试信息
    INFO,       // 普通信息
    WARNING,    // 警告信息
    ERROR,      // 错误信息
    SUCCESS,    // 成功信息
    VERBOSE,    // 详细信息
};

constexpr int getAndroidLogLevel(LogType type) {
    switch (type) {
        case LogType::DEBUG:   return ANDROID_LOG_DEBUG;
        case LogType::INFO:    return ANDROID_LOG_INFO;
        case LogType::WARNING: return ANDROID_LOG_WARN;
        case LogType::ERROR:   return ANDROID_LOG_ERROR;
        case LogType::VERBOSE: return ANDROID_LOG_VERBOSE;
        case LogType::SUCCESS: return ANDROID_LOG_INFO;
        default:               return ANDROID_LOG_INFO;
    }
}

constexpr const char* getLogTypeName(LogType type) {
    switch (type) {
        case LogType::DEBUG:   return "DEBUG";
        case LogType::INFO:    return "INFO";
        case LogType::WARNING: return "WARNING";
        case LogType::ERROR:   return "ERROR";
        case LogType::SUCCESS: return "SUCCESS";
        case LogType::VERBOSE: return "VERBOSE";
        default:               return "INFO";
    }
}

class AndroidLogger {
private:
    static void log_output(LogType type, const std::string& message) {
        int android_level = getAndroidLogLevel(type);
        const char* type_name = getLogTypeName(type);

        std::string formatted_message;
        if (type == LogType::SUCCESS) {
            formatted_message = "✅ " + message;
        } else if (type != LogType::INFO) {
            formatted_message = std::string("[") + type_name + "] " + message;
        } else {
            formatted_message = message;
        }

        __android_log_print(android_level, LOG_TAG, "%s", formatted_message.c_str());
    }

public:
    template<typename... Args>
    static void print(LogType type, const std::string& fmt, Args&&... args) {
        if constexpr (sizeof...(args) == 0) {
            log_output(type, fmt);
        } else {
            std::string formatted = std::vformat(fmt, std::make_format_args(args...));
            log_output(type, formatted);
        }
    }

    template<typename... Args>
    static void print(const std::string& fmt, Args&&... args) {
        print(LogType::INFO, fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void println(LogType type, const std::string& fmt, Args&&... args) {
        print(type, fmt + "\n", std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void println(const std::string& fmt, Args&&... args) {
        println(LogType::INFO, fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void print_all(LogType type, Args&&... args) {
        std::ostringstream oss;
        (oss << ... << std::forward<Args>(args));
        log_output(type, oss.str());
    }

    template<typename... Args>
    static void print_all(Args&&... args) {
        print_all(LogType::INFO, std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void println_all(LogType type, Args&&... args) {
        print_all(type, std::forward<Args>(args)...);
        log_output(type, ""); // 输出空行
    }

    template<typename... Args>
    static void println_all(Args&&... args) {
        println_all(LogType::INFO, std::forward<Args>(args)...);
    }

    template<typename Sep, typename... Args>
    static void print_separated(LogType type, Sep&& separator, Args&&... args) {
        std::ostringstream oss;
        bool first = true;
        auto add_with_sep = [&](const auto& item) {
            if (!first) oss << separator;
            oss << item;
            first = false;
        };

        (add_with_sep(std::forward<Args>(args)), ...);
        log_output(type, oss.str());
    }

    template<typename Sep, typename... Args>
    static void print_separated(Sep&& separator, Args&&... args) {
        print_separated(LogType::INFO, separator, std::forward<Args>(args)...);
    }

    template<typename Container>
    static void print_container(LogType type, const Container& container, const std::string& name = "Container") {
        std::ostringstream oss;
        oss << name << ": [";
        bool first = true;
        for (const auto& item : container) {
            if (!first) oss << ", ";
            oss << item;
            first = false;
        }
        oss << "]";
        log_output(type, oss.str());
    }

    template<typename Container>
    static void print_container(const Container& container, const std::string& name = "Container") {
        print_container(LogType::INFO, container, name);
    }

    template<typename... Args>
    static void print_error(const std::string& fmt, Args&&... args) {
        print(LogType::ERROR, fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void print_warning(const std::string& fmt, Args&&... args) {
        print(LogType::WARNING, fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void print_success(const std::string& fmt, Args&&... args) {
        print(LogType::SUCCESS, fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void print_debug(const std::string& fmt, Args&&... args) {
        print(LogType::DEBUG, fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void print_verbose(const std::string& fmt, Args&&... args) {
        print(LogType::VERBOSE, fmt, std::forward<Args>(args)...);
    }
};

template<typename... Args>
void android_print(LogType type, const std::string& fmt, Args&&... args) {
    AndroidLogger::print(type, fmt, std::forward<Args>(args)...);
}

template<typename... Args>
void android_print(const std::string& fmt, Args&&... args) {
    AndroidLogger::print(LogType::INFO, fmt, std::forward<Args>(args)...);
}

template<typename... Args>
void android_println(LogType type, const std::string& fmt, Args&&... args) {
    AndroidLogger::println(type, fmt, std::forward<Args>(args)...);
}

template<typename... Args>
void android_println(const std::string& fmt, Args&&... args) {
    AndroidLogger::println(LogType::INFO, fmt, std::forward<Args>(args)...);
}

#endif // ANDROID_LOGGER_HPP