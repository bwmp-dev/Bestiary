@Library('bwmp') _

// Bestiary is a cross-platform build: Keystone AND Adventure are shaded and
// relocated into the plugin. The jar checks below are what catch a relocation
// regression, which compiles perfectly and only fails at runtime.
//
// The AI modules are checked for separately. They are shaded in as ordinary
// classes rather than relocated, and their absence would not fail the build —
// it would just quietly mean no custom AI on any server.
mavenPlugin(
    artifacts: 'bestiary-plugin/target/Bestiary-*.jar,bestiary-api/target/bestiary-api-*.jar',
    verify: [
        jar:       'bestiary-plugin/target/Bestiary-*.jar',
        relocated: ['dev/bwmp/bestiary/libs/keystone/', 'dev/bwmp/bestiary/libs/kyori/'],
        absent:    ['net/kyori/', 'dev/bwmp/keystone/'],
        present:   [
            'dev/bwmp/bestiary/BestiaryPlugin.class',
            'dev/bwmp/bestiary/ai/PaperAiController.class',
            'dev/bwmp/bestiary/ai/nms/NmsNavigation.class'
        ]
    ]
)
