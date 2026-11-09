package br.com.jhcomp.planner.video;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Onde os vídeos ficam e como chamar cada motor.
 *
 * <p>Os três comandos são modelos com marcadores ({text}, {audio}, {video}, {out}); vazio
 * desliga a etapa. Ver docs/content-studio/08-deployment-and-operations.md.</p>
 */
@ConfigurationProperties(prefix = "jhcomp.video")
public class VideoProperties {

    private String dir = "uploads/video";
    private String ffmpeg = "ffmpeg";
    private long stepTimeoutMinutes = 60;
    private String voiceCommand = "";
    private String lipsyncCommand = "";
    private String transcribeCommand = "";

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }
    public String getFfmpeg() { return ffmpeg; }
    public void setFfmpeg(String ffmpeg) { this.ffmpeg = ffmpeg; }
    public long getStepTimeoutMinutes() { return stepTimeoutMinutes; }
    public void setStepTimeoutMinutes(long stepTimeoutMinutes) { this.stepTimeoutMinutes = stepTimeoutMinutes; }
    public String getVoiceCommand() { return voiceCommand; }
    public void setVoiceCommand(String voiceCommand) { this.voiceCommand = voiceCommand; }
    public String getLipsyncCommand() { return lipsyncCommand; }
    public void setLipsyncCommand(String lipsyncCommand) { this.lipsyncCommand = lipsyncCommand; }
    public String getTranscribeCommand() { return transcribeCommand; }
    public void setTranscribeCommand(String transcribeCommand) { this.transcribeCommand = transcribeCommand; }
}
