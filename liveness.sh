#!/bin/sh

PORT="7777"
islive () {
health=`/usr/bin/wget -O - -q http://localhost:${PORT}/actuator/health  | grep  -B 1 '"status"' |  awk -F '"'  '{print $4}'`

if [[ "${health}"x == "UP"x  ]]; then
echo "health success"  ### 返回值是 UP
exit 0                 ### 退出码 为0 即成功
else
echo "health err"      ### 返回值不是 UP
exit 1                 ### 退出码 为1 即失败
fi
}
islive