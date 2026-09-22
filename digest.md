### :x: `Build TDLib (native)` failed

Run: https://github.com/mrmusic7654-wq/Master/actions/runs/35698311192

Extracted errors:

```
ERROR: expected libtdjson.so was not produced under /home/runner/work/Master/Master/build/tdlib/build-arm64-v8a
```

<details><summary>tail of tdlib-build.log</summary>

```
[11/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/platform.cpp.o
[12/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/MemoryMapping.cpp.o
[13/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/FileFd.cpp.o
[14/89] Building CXX object tdtl/CMakeFiles/tdtl.dir/td/tl/tl_generate.cpp.o
[15/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/PollFlags.cpp.o
[16/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/path.cpp.o
[17/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/rlimit.cpp.o
[18/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/IPAddress.cpp.o
[19/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/ServerSocketFd.cpp.o
[20/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/sleep.cpp.o
[21/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/signals.cpp.o
[22/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/Stat.cpp.o
[23/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/stacktrace.cpp.o
[24/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/SocketFd.cpp.o
[25/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/wstring_convert.cpp.o
[26/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/thread_local.cpp.o
[27/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/EventFdBsd.cpp.o
[28/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/UdpSocketFd.cpp.o
[29/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/EventFdWindows.cpp.o
[30/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/Iocp.cpp.o
[31/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/KQueue.cpp.o
[32/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/uname.cpp.o
[33/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/Poll.cpp.o
[34/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/Select.cpp.o
[35/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/user.cpp.o
[36/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/StdStreams.cpp.o
[37/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/WineventPoll.cpp.o
[38/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/generate/auto/mime_type_to_extension.cpp.o
[39/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/Epoll.cpp.o
[40/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/EventFdLinux.cpp.o
[41/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/generate/auto/extension_to_mime_type.cpp.o
[42/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/NativeFd.cpp.o
[43/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/ThreadPthread.cpp.o
[44/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/port/detail/ThreadIdGuard.cpp.o
[45/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/BigNum.cpp.o
[46/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/BufferedUdp.cpp.o
[47/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/buffer.cpp.o
[48/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/check.cpp.o
[49/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/AsyncFileLog.cpp.o
[50/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/Ed25519.cpp.o
[51/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/base64.cpp.o
[52/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/crypto.cpp.o
[53/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/ExitGuard.cpp.o
[54/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/emoji.cpp.o
[55/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/FlatHashTable.cpp.o
[56/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/FloodControlGlobal.cpp.o
[57/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/find_boundary.cpp.o
[58/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/filesystem.cpp.o
[59/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/FileLog.cpp.o
[60/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/Gzip.cpp.o
[61/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/GzipByteFlow.cpp.o
[62/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/HttpDate.cpp.o
[63/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/HttpUrl.cpp.o
[64/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/logging.cpp.o
[65/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/Hints.cpp.o
[66/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/misc.cpp.o
[67/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/MpmcQueue.cpp.o
[68/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/PathView.cpp.o
[69/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/JsonBuilder.cpp.o
[70/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/SharedSlice.cpp.o
[71/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/Random.cpp.o
[72/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/Slice.cpp.o
[73/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/StackAllocator.cpp.o
[74/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/Status.cpp.o
[75/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/StringBuilder.cpp.o
[76/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/OptionParser.cpp.o
[77/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/tl_parsers.cpp.o
[78/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/Time.cpp.o
[79/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/Timer.cpp.o
[80/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/TsFileLog.cpp.o
[81/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/TsLog.cpp.o
[82/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/TsCerr.cpp.o
[83/89] Linking CXX static library tdtl/libtdtl.a
[84/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/unicode.cpp.o
[85/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/tests.cpp.o
[86/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/MimeType.cpp.o
[87/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/utf8.cpp.o
[88/89] Building CXX object tdutils/CMakeFiles/tdutils.dir/td/utils/translit.cpp.o
[89/89] Linking CXX static library tdutils/libtdutils.a
ERROR: expected libtdjson.so was not produced under /home/runner/work/Master/Master/build/tdlib/build-arm64-v8a
```

</details>

<details><summary>tail of ci-diagnostics/sdk-setup.log</summary>

```
SDK root: /usr/local/lib/android/sdk
sdkmanager: /usr/local/lib/android/sdk/cmdline-tools/latest/bin/sdkmanager
yes: standard output: Broken pipe
installing: platforms;android-35 build-tools;35.0.0 platform-tools ndk;26.3.11579264 cmake;3.22.1
Loading package information...                                                  Loading local repository...                                                     [                                       ] 3% Loading local repository...        [                                       ] 3% Fetch remote repository...         [=                                      ] 3% Fetch remote repository...         [=                                      ] 4% Fetch remote repository...         [=                                      ] 5% Fetch remote repository...         [==                                     ] 5% Fetch remote repository...         [==                                     ] 6% Fetch remote repository...         [==                                     ] 7% Fetch remote repository...         [==                                     ] 7% Computing updates...               [===                                    ] 8% Computing updates...               [===                                    ] 10% Computing updates...              [===                                    ] 10% Installing CMake 3.22.1           [===                                    ] 10% Downloading cmake-3.22.1-linux.zip[====                                   ] 10% Downloading cmake-3.22.1-linux.zip[====                                   ] 11% Downloading cmake-3.22.1-linux.zip[====                                   ] 12% Downloading cmake-3.22.1-linux.zip[=====                                  ] 13% Downloading cmake-3.22.1-linux.zip[=====                                  ] 14% Downloading cmake-3.22.1-linux.zip[=====                                  ] 15% Downloading cmake-3.22.1-linux.zip[======                                 ] 15% Downloading cmake-3.22.1-linux.zip[======                                 ] 16% Downloading cmake-3.22.1-linux.zip[======                                 ] 17% Downloading cmake-3.22.1-linux.zip[=======                                ] 18% Downloading cmake-3.22.1-linux.zip[=======                                ] 19% Downloading cmake-3.22.1-linux.zip[=======                                ] 20% Downloading cmake-3.22.1-linux.zip[========                               ] 20% Downloading cmake-3.22.1-linux.zip[========                               ] 21% Downloading cmake-3.22.1-linux.zip[========                               ] 21% Unzipping...                      [========                               ] 21% Unzipping... bin/cmake            [========                               ] 22% Unzipping... bin/cmake            [=========                              ] 23% Unzipping... bin/cmake            [=========                              ] 24% Unzipping... bin/cmake            [=========                              ] 24% Unzipping... bin/ctest            [=========                              ] 25% Unzipping... bin/ctest            [==========                             ] 25% Unzipping... bin/ctest            [==========                             ] 26% Unzipping... bin/ctest            [==========                             ] 27% Unzipping... bin/ctest            [===========                            ] 28% Unzipping... bin/ctest            [===========                            ] 28% Unzipping... bin/cpack            [===========                            ] 29% Unzipping... bin/cpack            [===========                            ] 30% Unzipping... bin/cpack            [============                           ] 30% Unzipping... bin/cpack            [============                           ] 31% Unzipping... bin/cpack            [============                           ] 31% Unzipping... share/vim/vimfiles/in[============                           ] 31% Unzipping... share/vim/vimfiles/sy[============                           ] 31% Unzipping... share/aclocal/cmake.m[============                           ] 31% Unzipping... share/emacs/site-lisp[============                           ] 31% Unzipping... share/cmake-3.22/incl[============                           ] 31% Unzipping... share/cmake-3.22/Help[============                           ] 31% Unzipping... share/cmake-3.22/Temp[============                           ] 32% Unzipping... share/cmake-3.22/Temp[============                           ] 32% Unzipping... share/cmake-3.22/Modu[============                           ] 32% Unzipping... share/bash-completion[============                           ] 32% Unzipping... doc/cmake-3.22/Copyri[============                           ] 32% Unzipping... doc/cmake-3.22/cmzlib[============                           ] 32% Unzipping... doc/cmake-3.22/cmzstd[============                           ] 32% Unzipping... doc/cmake-3.22/cmnght[============                           ] 32% Unzipping... doc/cmake-3.22/cmsys/[============                           ] 32% Unzipping... doc/cmake-3.22/cmcurl[============                           ] 32% Unzipping... doc/cmake-3.22/cmlibr[============                           ] 32% Unzipping... doc/cmake-3.22/cmliba[============                           ] 32% Unzipping... doc/cmake-3.22/cmlibl[============                           ] 32% Unzipping... doc/cmake-3.22/cmlibu[============                           ] 32% Unzipping... doc/openssl-1.1.1l/LI[============                           ] 32% Unzipping... source.properties    [============                           ] 32% Unzipping... bin/ninja            [============                           ] 32% Unzipping... doc/ninja/LICENSE    [============                           ] 32% Unzipping... share/cmake-3.22/Modu[=============                          ] 33% Unzipping... share/cmake-3.22/Modu[=====================                  ] 55% Unzipping... share/cmake-3.22/Modu[=====================                  ] 55% Installing NDK (Side by side) 26.3[=====================                  ] 55% Downloading android-ndk-r26d-linux[======================                 ] 55% Downloading android-ndk-r26d-linux[======================                 ] 56% Downloading android-ndk-r26d-linux[======================                 ] 57% Downloading android-ndk-r26d-linux[=======================                ] 58% Downloading android-ndk-r26d-linux[=======================                ] 59% Downloading android-ndk-r26d-linux[=======================                ] 60% Downloading android-ndk-r26d-linux[========================               ] 60% Downloading android-ndk-r26d-linux[========================               ] 61% Downloading android-ndk-r26d-linux[========================               ] 62% Downloading android-ndk-r26d-linux[=========================              ] 63% Downloading android-ndk-r26d-linux[=========================              ] 64% Downloading android-ndk-r26d-linux[=========================              ] 65% Downloading android-ndk-r26d-linux[==========================             ] 65% Downloading android-ndk-r26d-linux[==========================             ] 66% Downloading android-ndk-r26d-linux[==========================             ] 66% Unzipping... share/cmake-3.22/Modu[==========================             ] 66% Unzipping... android-ndk-r26d/    [==========================             ] 66% Unzipping... android-ndk-r26d/CHAN[==========================             ] 66% Unzipping... android-ndk-r26d/wrap[==========================             ] 66% Unzipping... android-ndk-r26d/preb[==========================             ] 66% Unzipping... android-ndk-r26d/buil[==========================             ] 66% Unzipping... android-ndk-r26d/NOTI[==========================             ] 66% Unzipping... android-ndk-r26d/ndk-[==========================             ] 66% Unzipping... android-ndk-r26d/NOTI[==========================             ] 66% Unzipping... android-ndk-r26d/meta[==========================             ] 66% Unzipping... android-ndk-r26d/ndk-[==========================             ] 66% Unzipping... android-ndk-r26d/pyth[==========================             ] 66% Unzipping... android-ndk-r26d/sour[==========================             ] 66% Unzipping... android-ndk-r26d/simp[==========================             ] 67% Unzipping... android-ndk-r26d/simp[==========================             ] 67% Unzipping... android-ndk-r26d/ndk-[==========================             ] 67% Unzipping... android-ndk-r26d/READ[==========================             ] 67% Unzipping... android-ndk-r26d/tool[===========================            ] 68% Unzipping... android-ndk-r26d/tool[===========================            ] 69% Unzipping... android-ndk-r26d/tool[===========================            ] 70% Unzipping... android-ndk-r26d/tool[============================           ] 70% Unzipping... android-ndk-r26d/tool[============================           ] 71% Unzipping... android-ndk-r26d/tool[============================           ] 72% Unzipping... android-ndk-r26d/tool[=============================          ] 73% Unzipping... android-ndk-r26d/tool[=============================          ] 74% Unzipping... android-ndk-r26d/tool[=============================          ] 75% Unzipping... android-ndk-r26d/tool[==============================         ] 75% Unzipping... android-ndk-r26d/tool[==============================         ] 76% Unzipping... android-ndk-r26d/tool[==============================         ] 77% Unzipping... android-ndk-r26d/tool[==============================         ] 77% Unzipping... android-ndk-r26d/ndk-[==============================         ] 77% Unzipping... android-ndk-r26d/shad[==============================         ] 78% Unzipping... android-ndk-r26d/shad[=======================================] 100% Unzipping... android-ndk-r26d/sha

SDK ready at /usr/local/lib/android/sdk
```

</details>


Raw logs branch: `ci/diag-native-tdlib` (fetch with
`git fetch origin ci/diag-native-tdlib && git show FETCH_HEAD --stat`).
