package jdd.lunarProject.Game;
public enum GameState {
    WAITING,   // 等待中 (人数不足)
    STARTING,  // 倒计时中 (人数已达标，正在准备开始)
    STARTED,   // 游戏中 (已经生成关卡并开始刷怪)
    ENDED      // 已结束 (结算阶段，5秒后销毁)
}