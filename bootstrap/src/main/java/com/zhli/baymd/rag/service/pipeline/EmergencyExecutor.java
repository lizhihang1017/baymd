package com.zhli.baymd.rag.service.pipeline;

import com.zhli.baymd.rag.core.guardrails.EmergencyDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 紧急分诊执行器 — 红旗症状短路处理。
 *
 * <p>不调用知识库检索、不进入 Agent 循环，直接流式输出固定急救指引模板：
 * 识别风险 → 立即就医/拨打 120 → 简要现场处置 → 免责声明。
 * 自杀/自残类额外输出心理援助热线。优先级最高（{@link ExecutionMode#EMERGENCY}）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmergencyExecutor implements ConversationExecutor {

    /** 全国心理援助热线（24 小时） */
    private static final String PSYCHOLOGY_HOTLINE = "12356";

    @Override
    public ExecutionMode getMode() {
        return ExecutionMode.EMERGENCY;
    }

    @Override
    public boolean supports(StreamChatContext ctx) {
        return ctx.getEmergency() != null && ctx.getEmergency().detected();
    }

    @Override
    public void execute(StreamChatContext ctx) {
        EmergencyDetector.EmergencyResult result = ctx.getEmergency();
        log.warn("EmergencyExecutor 接管: category={}, matched={}",
                result.category(), result.matchedKeywords());

        // 标记本次响应为紧急（前端据此渲染红色警示卡片）
        try {
            ctx.getCallback().setEmergency(true);
        } catch (Exception e) {
            log.warn("标记紧急响应失败（不影响主流程）", e);
        }

        String content = buildEmergencyContent(result);
        ctx.getCallback().onContent(content);
        ctx.getCallback().onComplete();
    }

    private String buildEmergencyContent(EmergencyDetector.EmergencyResult result) {
        EmergencyDetector.Category cat = result.category();
        StringBuilder sb = new StringBuilder();
        sb.append("## ⚠️ 请立即就医\n\n");
        sb.append(firstAidLead(cat));
        sb.append("\n\n### 一、立即行动\n");
        sb.append("- **立刻拨打 120 急救电话**，或由身边人护送至最近医院急诊\n");
        sb.append("- 保持通话畅通，向急救人员说明症状与发生时间\n");
        sb.append("- 切勿自行驾车前往医院\n\n");
        sb.append("### 二、现场处置\n");
        sb.append(onSiteMeasure(cat));
        sb.append("\n\n### 三、重要提醒\n");
        sb.append("- 以上为紧急就医指引，**不能替代专业医疗诊断与现场救治**\n");
        sb.append("- 等待急救期间持续观察患者意识与呼吸\n");

        if (cat == EmergencyDetector.Category.SELF_HARM) {
            sb.append("\n---\n\n");
            sb.append("## 💙 你不是一个人\n\n");
            sb.append("如果你正经历情绪痛苦或有伤害自己的念头，请立刻寻求帮助：\n\n");
            sb.append("- **全国心理援助热线（24 小时）：").append(PSYCHOLOGY_HOTLINE).append("**\n");
            sb.append("- 也可以联系家人、朋友，或前往最近医院急诊\n");
            sb.append("- 你的感受是真实的，也值得被帮助，请给自己一个机会\n");
        }
        return sb.toString();
    }

    private String firstAidLead(EmergencyDetector.Category cat) {
        return switch (cat) {
            case CARDIOVASCULAR -> "您描述的症状（胸痛/胸闷/压榨感）可能提示心血管急症（如心肌梗死），"
                    + "**这类情况可能危及生命，必须立即就医。**";
            case RESPIRATORY -> "您描述的呼吸困难/喘不上气可能提示严重呼吸或心肺急症，"
                    + "**可能迅速危及生命，必须立即就医。**";
            case NEUROLOGICAL -> "您描述的意识改变/抽搐/肢体无力/言语不清可能提示中枢神经急症"
                    + "（如脑卒中、癫痫持续状态），**时间窗关键，必须立即就医。**";
            case HEMORRHAGE -> "您描述的严重出血需要立即止血并就医，**大量失血可迅速危及生命。**";
            case POISONING -> "您描述的情况可能为中毒或药物过量，**请立即就医并携带相关药物/毒物信息。**";
            case ANAPHYLAXIS -> "您描述的严重过敏反应（可能伴喉头水肿/呼吸困难）属于过敏性休克，"
                    + "**几分钟内即可危及生命，必须立即就医。**";
            case SELF_HARM -> "检测到您可能正在经历自我伤害相关的危机，请立即寻求帮助。";
            case OTHER -> "您描述的症状可能提示危急情况，**为安全起见请立即就医。**";
        };
    }

    private String onSiteMeasure(EmergencyDetector.Category cat) {
        return switch (cat) {
            case CARDIOVASCULAR -> "- 让患者**立即停止活动、静坐或半卧位休息**\n"
                    + "- 解开领口、保持空气流通\n"
                    + "- 如有医嘱含服硝酸甘油，可按医嘱使用；**不要**随意给水或药物\n"
                    + "- 若患者意识丧失、无呼吸，立即心肺复苏（CPR）\n";
            case RESPIRATORY -> "- 协助患者取**坐位或半坐位**，身体前倾便于呼吸\n"
                    + "- 保持气道通畅，解开紧身衣物\n"
                    + "- 如有备用哮喘吸入剂，按医嘱使用\n"
                    + "- 保持镇静，过度紧张会加重呼吸困难\n";
            case NEUROLOGICAL -> "- 让患者**侧卧**，防止呕吐物误吸\n"
                    + "- **不要**往嘴里塞任何物品（包括手指、勺子）\n"
                    + "- 记录症状开始时间（脑卒中溶栓时间窗关键）\n"
                    + "- 不要强行按压抽搐肢体\n";
            case HEMORRHAGE -> "- 用干净布料**直接压迫出血点**止血\n"
                    + "- 四肢出血可抬高患肢\n"
                    + "- 不要自行取出嵌入伤口的异物\n";
            case POISONING -> "- **不要**催吐（尤其误食腐蚀性物质时）\n"
                    + "- 保留毒物容器/剩余药物，就医时带给医生参考\n"
                    + "- 若意识改变，侧卧防误吸\n";
            case ANAPHYLAXIS -> "- 如有肾上腺素自动注射器（EpiPen），按医嘱立即使用\n"
                    + "- 保持平卧，抬高下肢\n"
                    + "- 若呼吸困难可半坐位\n";
            case SELF_HARM -> "- 请立即拨打心理援助热线或联系身边信任的人\n"
                    + "- 远离可能造成伤害的物品\n"
                    + "- 不要独自承受，等待专业人员帮助\n";
            case OTHER -> "- 让患者保持安静、避免活动\n"
                    + "- 密切观察意识与呼吸变化\n"
                    + "- 如意识丧失且无呼吸，立即心肺复苏\n";
        };
    }
}
