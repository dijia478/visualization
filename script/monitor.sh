#!/bin/bash

# 初始化数组
declare -A hour_uv
declare -A hour_pv
for i in {00..23}; do
  hour_uv[$i]=0
  hour_pv[$i]=0 
done


if [[ ${1} == '' ]]
then
  log_path="./logs/visualization.log"
else
  log_path="./logs/visualization-${1}*"
fi  

echo ${log_path}

# 读取日志
zgrep '访问人数' ${log_path} > log.txt


# 遍历日志
while read line; do

  # 提取时间、访问量、请求量
  time=$(echo $line | awk '{print $7}')
  hour=${time:0:2}

  uv=$(echo $line | awk -F '，' '{print $2}' | awk -F '：' '{print $2}')
  pv=$(echo $line | awk -F '，' '{print $3}' | awk -F '：' '{print $2}')
  
  # 判断首次统计
  if [ -z ${hour_uv[$hour]} ]; then
    hour_uv[$hour]=$uv
  else 
    hour_uv[$hour]=$((${hour_uv[$hour]} + $uv))
  fi

  if [ -z ${hour_pv[$hour]} ]; then
   hour_pv[$hour]=$pv
  else
    hour_pv[$hour]=$((${hour_pv[$hour]} + $pv))
  fi

done < log.txt

# 输出结果
total_uv=0
total_pv=0
for i in {00..23}; do
  total_uv=$((total_uv + ${hour_uv[$i]}))
  total_pv=$((total_pv + ${hour_pv[$i]}))
  echo -e "$i时\t访问人数:${hour_uv[$i]}\t请求数量:${hour_pv[$i]}"
done
echo -e "总计\t访问人数:${total_uv}\t请求数量:${total_pv}"

rm -rf log.txt