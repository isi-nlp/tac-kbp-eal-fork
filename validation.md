Instructions for processing input repositories:
`tac-kbp-eal` refers to your clone of the github repository or the `tac-kbp-eal` subproject.
Any docidmap is a File with, one per line, a document id, a tab character, and a path to an existing file.

1. Convert to BBN IDs using `tac-kbp-eal/target/appassembler/bin/importForeignIDs`:  
    Params:

    ```
    input: /path/to/your/system/output
    output: /path/to/internal/id/output
    doMultipleStores: false
    outputLayout: KBP_EAL_2016
    ```

2. Filter out invalid event types/roles using `tac-kbp-eal/target/appassembler/bin/filterOutInvalidArguments`:  
    Params:

    ```
    outputLayout: KBP_EAL_2016
    layout: KBP_EAL_2016
    input: output of last step
    output: where ever you want it
    alwaysValidRoles: Place,Time
    validRoles: tac-kbp-eal/data/2016.types.txt
    ```

3. Filter `Generic` responses from linking store & remove anything that dies from the CorpusLinking using `tac-kbp-eal/target/appassembler/bin/filterLinkingStore`:  
    Params:

    ```
    inputStore: last stage
    outputStore: where do you want it?
    ```
    
4. Validate system output using `tac-kbp-eal/target/appassembler/bin/validateSystemOutput2016`:  
    Params:

    ```
    systemOutputStore: last stage
    dump: false
    docIDMap: a map of docid to raw text
    validRoles: tac-kbp-eal/data/2016.types.txt
    linkableTypes: tac-kbp-eal/data/2016.linkable.txt
    ```
    
5. Minimize system outputs to just the best it produced using `tac-kbp-eal/target/appassembler/bin/keepOnlyBestResponses`:  
    Params:

    ```
    inputStore: input to validation stage
    outputStore: keepBestOutput
    outputLayout: KBP_EAL_2016
    keepInferenceCases: false
    ```
    
6. Extract query responses using `tac-kbp-eal/target/appassembler/bin/queryResponseFromERE`:  
    Params (these may change come the evaluation and feedback from the LDC):
    ```
    com.bbn.tac.eal.storeDir: /output/dir/of/keep/Best
    com.bbn.tac.eal.storesToProcess: systemNamesInTheAboveDirectory
    com.bbn.tac.eal.outputFile: outputFile
    com.bbn.tac.eal.eremap: ere docid to filepath map.
    com.bbn.tac.eal.queryFile: input queries
    com.bbn.tac.eal.slack: 300
    com.bbn.tac.eal.matchBestCASTypesOnly: false
    com.bbn.tac.eal.minNominalCASOverlap: 0.3
    com.bbn.tac.eal.maxResponsesPerQueryPerSystem: 200
    ```