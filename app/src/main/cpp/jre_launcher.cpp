//
// Created by qz919 on 2025/9/29.
//

#include <jni.h>
#include <dlfcn.h>
#include <vector>
#include <string>
#include <string_view>
#include <filesystem>
#include <sys/wait.h>
#include <android/log.h>
#include <asm-generic/fcntl.h>
#include <fcntl.h>
#include <poll.h>

#include "android_log.hpp"

static volatile sig_atomic_t child_pid = -1;
static volatile sig_atomic_t signal_received = 0;

static void signal_handler(int sig) {
    signal_received = sig;
    if (child_pid > 0) {
        kill(child_pid, SIGTERM);
    }
}

static int setup_signal_handlers() {
    struct sigaction sa{};

    sa.sa_handler = signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART;

    if (sigaction(SIGINT, &sa, nullptr) == -1) {
        return -1;
    }
    if (sigaction(SIGTERM, &sa, nullptr) == -1) {
        return -1;
    }

    sa.sa_handler = SIG_IGN;
    if (sigaction(SIGPIPE, &sa, nullptr) == -1) {
        return -1;
    }

    return 0;
}

int stopJvm() {
    if (child_pid > 0) {
        android_println(LogType::DEBUG, "Stopping child process (PID: {})", static_cast<int>(child_pid));

        if (kill(child_pid, SIGTERM) == 0) {
            signal_received = SIGTERM;
            return 0;
        } else {
            perror("kill");
            return -1;
        }
    } else {
        android_println(LogType::DEBUG, "No child process to stop");
    }
    return -1;
}

static int launchJvm(char **argv) {
    if (setup_signal_handlers() == -1) {
        perror("sigaction");
        return -1;
    }

    signal_received = 0;
    child_pid = -1;

    int pipefd[2];
    if (pipe(pipefd) == -1) {
        perror("pipe");
        return -1;
    }

    int flags = fcntl(pipefd[0], F_GETFL, 0);
    fcntl(pipefd[0], F_SETFL, flags | O_NONBLOCK);

    pid_t pid = fork();

    if (pid == -1) {
        perror("fork");
        close(pipefd[0]);
        close(pipefd[1]);
        return -1;
    } else if (pid == 0) {
        struct sigaction sa{};
        sa.sa_handler = SIG_DFL;
        sigemptyset(&sa.sa_mask);
        sa.sa_flags = 0;
        sigaction(SIGINT, &sa, nullptr);
        sigaction(SIGTERM, &sa, nullptr);

        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        close(pipefd[1]);

        execvp(argv[0], argv);
        perror("execvp");
        exit(EXIT_FAILURE);
    } else {
        child_pid = pid;
        close(pipefd[1]);

        char buffer[1024];
        ssize_t bytes_read;
        struct pollfd fds[1];
        fds[0].fd = pipefd[0];
        fds[0].events = POLLIN;

        while (true) {
            int ret = poll(fds, 1, 100);

            if (signal_received) {
                android_println(LogType::DEBUG, "Signal received, breaking loop");
                break;
            }

            if (ret == -1) {
                if (errno == EINTR) {
                    if (signal_received) {
                        break;
                    }
                    continue;
                }
                perror("poll");
                break;
            } else if (ret == 0) {
                int status;
                pid_t result = waitpid(pid, &status, WNOHANG);
                if (result == pid) {
                    android_println(LogType::DEBUG, "Child process exited normally");
                    break;
                } else if (result == -1) {
                    perror("waitpid");
                    break;
                }
                continue;
            }

            if (fds[0].revents & POLLIN) {
                bytes_read = read(pipefd[0], buffer, sizeof(buffer));
                if (bytes_read > 0) {
                    if (write(STDOUT_FILENO, buffer, bytes_read) == -1) {
                        if (errno == EPIPE) {
                            break;
                        }
                    }
                } else if (bytes_read == 0) {
                    android_println(LogType::DEBUG, "Pipe EOF, child process finished");
                    break;
                } else if (bytes_read == -1) {
                    if (errno != EAGAIN && errno != EWOULDBLOCK) {
                        perror("read");
                        break;
                    }
                }
            }

            if (fds[0].revents & (POLLERR | POLLHUP)) {
                android_println(LogType::DEBUG, "Pipe error or hangup");
                break;
            }
        }

        close(pipefd[0]);

        int status;
        int wait_timeout = 5000;

        if (signal_received) {
            android_println(LogType::DEBUG, "Sending SIGKILL to child process");
            kill(pid, SIGKILL);

            pid_t result;
            int waited = 0;
            while (waited < wait_timeout) {
                result = waitpid(pid, &status, WNOHANG);
                if (result == pid) {
                    break; // 子进程已退出
                } else if (result == -1) {
                    perror("waitpid after SIGKILL");
                    break;
                }
                usleep(100000); // 睡眠100ms
                waited += 100;
            }

            if (waited >= wait_timeout) {
                android_println(LogType::ERROR, "Timeout waiting for child process to terminate");
            }

            child_pid = -1;
            return -1;
        }

        if (waitpid(pid, &status, 0) == -1) {
            perror("waitpid");
            child_pid = -1;
            return -1;
        }

        child_pid = -1;

        if (WIFSIGNALED(status)) {
            fprintf(stderr, "Child process terminated by signal: %d\n", WTERMSIG(status));
            return -1;
        }

        return WEXITSTATUS(status);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_eurya_awt_utils_NativeJavaLauncher_dup2(JNIEnv *env, jclass thiz, jstring jfile) {
    const char *file = env->GetStringUTFChars(jfile, nullptr);

    int fd = open(file, O_WRONLY | O_CREAT | O_TRUNC, 0666);
    dup2(fd, STDERR_FILENO);
    dup2(fd, STDOUT_FILENO);

    env->ReleaseStringUTFChars(jfile, file);
}


extern "C" JNIEXPORT void JNICALL
Java_io_github_eurya_awt_utils_NativeJavaLauncher_export(JNIEnv *env, jclass thiz, jstring jenvName,
                                                         jstring jenvValue) {
    const char *env_name = env->GetStringUTFChars(jenvName, nullptr);
    const char *env_value = env->GetStringUTFChars(jenvValue, nullptr);

    auto ret = setenv(env_name, env_value, 1);
    if (ret != 0) {
        android_println(LogType::ERROR, "Error: Failed to set environment variable: {}", env_name);
    } else {
        android_println(LogType::SUCCESS, "Successfully set environment variable: {}", env_name);
    }

    env->ReleaseStringUTFChars(jenvName, env_name);
    env->ReleaseStringUTFChars(jenvValue, env_value);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_eurya_awt_utils_NativeJavaLauncher_chdir(JNIEnv *env, jclass thiz, jstring jname) {
    const char *name = env->GetStringUTFChars(jname, nullptr);

    chdir(name);

    env->ReleaseStringUTFChars(jname, name);
}

extern "C" JNIEXPORT int JNICALL
Java_io_github_eurya_awt_utils_NativeJavaLauncher_nativeLaunchJvm(JNIEnv *env, jclass thiz,
                                                                  jobjectArray jargs) {
    jsize argc = env->GetArrayLength(jargs);

    if (argc <= 0) {
        android_println(LogType::ERROR, "Error: No arguments provided to JVM");
        return -1;
    }

    std::vector<std::string> args;
    std::vector<char *> argv;

    args.reserve(argc);
    argv.reserve(argc + 1);  // +1 for null terminator

    for (jsize i = 0; i < argc; i++) {
        auto str = reinterpret_cast<jstring>(env->GetObjectArrayElement(jargs, i));
        if (str == nullptr) {
            android_println(LogType::DEBUG, "Warning: Argument {} is null, using empty string", i);
            args.emplace_back("");
            continue;
        }

        const char *utf_chars = env->GetStringUTFChars(str, nullptr);
        if (utf_chars == nullptr) {
            android_println(LogType::DEBUG, "Error: Failed to get UTF chars for argument {}", i);
            env->DeleteLocalRef(str);
            return -1;
        }

        args.emplace_back(utf_chars);
        env->ReleaseStringUTFChars(str, utf_chars);
        env->DeleteLocalRef(str);
    }

    for (auto &arg: args) {
        argv.push_back(const_cast<char *>(arg.c_str()));
    }
    argv.push_back(nullptr);  // null terminator

    android_println("Prepared {} arguments for JVM launch:", argc);

    int result = launchJvm(argv.data());

    android_println("JVM execution completed with result: {}", result);
    return result;
}
extern "C" JNIEXPORT void JNICALL
Java_io_github_eurya_awt_utils_NativeJavaLauncher_nativeStopJvm(JNIEnv *env, jclass thiz) {
    if (stopJvm() != -1) {
        android_println(LogType::SUCCESS, "Successfully closed Java process activity");
    } else {
        android_println(LogType::ERROR, "Failed to close Java process activity");
    }
}