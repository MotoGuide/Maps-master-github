package org.zarroboogs.maps.beans;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 摩托车抓拍摄像头违规类型（与 6.2.11 规定的 13 种固定值一致）。
 * 图标文件名使用拼音，位于 drawable-xhdpi。
 */
public class CameraNoteType {

    public static final String CHUANG_HONG_DENG   = "闯红灯";
    public static final String KAO_YOU_XING_SHI   = "靠右行驶";
    public static final String GONG_JIAO_CHE_DAO  = "公交车道";
    public static final String FEI_JI_DONG_CHE_DAO = "非机动车道";
    public static final String CHUANG_XIAN_XING   = "闯限行";
    public static final String CHAO_SU            = "超速";
    public static final String WEI_GUI_BIAN_DAO   = "违规变道";
    public static final String NI_XING            = "逆行";
    public static final String BU_DAI_TOU_KUI     = "不戴头盔";
    public static final String YING_JI_CHE_DAO    = "应急车道";
    public static final String ZAO_YIN_CHAO_BIAO  = "噪音超标";
    public static final String YA_XIAN            = "压线";
    public static final String WEI_GUI_YONG_DENG  = "违规用灯";

    /** 13 种标准值，按顺序排列供 Spinner 使用 */
    public static final String[] STANDARD_VALUES = {
        CHUANG_HONG_DENG,
        KAO_YOU_XING_SHI,
        GONG_JIAO_CHE_DAO,
        FEI_JI_DONG_CHE_DAO,
        CHUANG_XIAN_XING,
        CHAO_SU,
        WEI_GUI_BIAN_DAO,
        NI_XING,
        BU_DAI_TOU_KUI,
        YING_JI_CHE_DAO,
        ZAO_YIN_CHAO_BIAO,
        YA_XIAN,
        WEI_GUI_YONG_DENG,
    };

    /** 历史 note 值 → 标准值 */
    private static final Map<String, String> LEGACY_MAP = new LinkedHashMap<>();
    static {
        LEGACY_MAP.put("不靠右", KAO_YOU_XING_SHI);
        LEGACY_MAP.put("不在辅路", KAO_YOU_XING_SHI);
        LEGACY_MAP.put("不在福路", KAO_YOU_XING_SHI);
        LEGACY_MAP.put("旅游右", "");
        LEGACY_MAP.put("走公交车道", GONG_JIAO_CHE_DAO);
        LEGACY_MAP.put("走非机动车道", FEI_JI_DONG_CHE_DAO);
        LEGACY_MAP.put("不带头盔", BU_DAI_TOU_KUI);
        LEGACY_MAP.put("闯红灯（燕山立交桥西东）", CHUANG_HONG_DENG);
    }

    /** 将历史或自定义 note 值标准化。空字符串代表"无类型"，使用默认图标。 */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        // 精确匹配标准值
        for (String std : STANDARD_VALUES) {
            if (std.equals(trimmed)) return std;
        }
        // 查历史映射
        String mapped = LEGACY_MAP.get(trimmed);
        if (mapped != null) return mapped;
        // 写错了的历史值（比如带括号后缀的闯红灯）——尝试包含匹配
        if (trimmed.startsWith("闯红灯")) return CHUANG_HONG_DENG;
        // 兜底：保留原值
        return trimmed;
    }

    /** 标准值 → 拼音图标资源名（不含扩展名），空字符串 → 老图标 */
    public static String toIconPinyin(String note) {
        if (note == null || note.isEmpty()) return null;
        switch (note) {
            case CHUANG_HONG_DENG:    return "chuanghongdeng";
            case KAO_YOU_XING_SHI:    return "bukaoyou";
            case GONG_JIAO_CHE_DAO:   return "gongjiaochedao";
            case FEI_JI_DONG_CHE_DAO: return "feijidongchedao";
            case CHUANG_XIAN_XING:    return "xianxing";
            case CHAO_SU:             return "chaosu";
            case WEI_GUI_BIAN_DAO:    return "weiguibiandao";
            case NI_XING:             return "nixing";
            case BU_DAI_TOU_KUI:      return "toukui";
            case YING_JI_CHE_DAO:     return "yingjichedao";
            case ZAO_YIN_CHAO_BIAO:   return "zaoyin";
            case YA_XIAN:             return "yaxian";
            case WEI_GUI_YONG_DENG:   return "buanguidingyongdeng";
            default:                  return null;
        }
    }
}
