package com.github.goldin.plugins.gradle.node.tasks

import org.gradle.api.logging.LogLevel


/**
 * Cleans Node.js generated files.
 */
class NodeCleanAllTask extends NodeBaseTask
{

    @Override
    void taskAction()
    {
        final userHome   = new File( System.getProperty( 'user.home' ))
        final deleteList = '.forever .npm .nvm'.tokenize().collect{ new File( userHome, it )}

        log( LogLevel.INFO ){ "Deleting $deleteList" }
        project.delete( deleteList as Object[] )
    }
}