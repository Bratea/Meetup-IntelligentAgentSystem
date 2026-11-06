package com.xunwu.agent.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * 把模型给出的原始 JSON 参数串原样交给处理函数的 {@link ToolCallback}。
 *
 * <p><b>为什么不用 Spring AI 自带的 {@code FunctionToolCallback}：</b>
 * 它的 {@code call(String, ToolContext)} 内部固定执行
 * {@code JsonParser.fromJson(toolInput, toolInputType)}，而 {@code inputType} 是必填项。
 * 本项目工具入参是 JSON 对象（如 {@code {"expression":"128*46"}}），
 * 但处理函数签名是 {@code String}（自行解析 JSON），
 * 于是 {@code fromJson(args, String.class)} 对 JSON 对象必然抛
 * {@code Conversion from JSON to java.lang.String failed}，工具调用 100% 失败。
 * 若把 {@code inputType} 改成 {@code Map.class}，则必须同步改造全部工具签名，
 * 且个别把参数序列化成字符串的模型会直接反序列化失败。</p>
 *
 * <p>这里绕过框架的类型转换，保证参数串原样到达工具，
 * 工具内部自行解析并自行兜底错误。</p>
 *
 * @author xunwu
 */
public final class RawJsonToolCallback implements ToolCallback {

    private final ToolDefinition definition;
    private final BiFunction<String, ToolContext, String> function;

    public RawJsonToolCallback(ToolDefinition definition,
                               BiFunction<String, ToolContext, String> function) {
        this.definition = definition;
        this.function = function;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        return function.apply(toolInput, new ToolContext(Map.of()));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return function.apply(toolInput, toolContext);
    }
}
