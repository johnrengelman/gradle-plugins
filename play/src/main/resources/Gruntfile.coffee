module.exports = ( grunt ) ->
  grunt.initConfig
    pkg: grunt.file.readJSON( '$packageJson' )

    # -----------------------------------------------
    # https://github.com/gruntjs/grunt-contrib-clean
    # -----------------------------------------------

    clean:
      build: [ '${ destinations.join( "', '" ) }' ]

    # -----------------------------------------------
    # https://github.com/gruntjs/grunt-contrib-coffee
    # -----------------------------------------------

    coffee:
      compileJoined:
        options:
          join     : true
          sourceMap: true
        <% if ( coffeeFiles ) { %>
        files:
          <%= coffeeFiles.collect{ key, value -> "'$key' : [ '${ value.join( "', '" ) }' ]" }.join( '\n          ' ) %>
        <% } %>

    # -----------------------------------------------
    # https://github.com/gruntjs/grunt-contrib-uglify
    # -----------------------------------------------

    uglify:
      options:
        mangle: false
  <% if ( uglifyFiles ) { %>
      build:
        files:
          <%= uglifyFiles.collect{ key, value -> "'$key' : [ '${ value.join( "', '" ) }' ]" }.join( '\n          ' ) %>
  <% } %>

  grunt.loadNpmTasks 'grunt-contrib-clean'
  grunt.loadNpmTasks 'grunt-contrib-coffee'
  grunt.loadNpmTasks 'grunt-contrib-uglify'
  grunt.loadNpmTasks 'grunt-contrib-less'
  grunt.registerTask 'default', [ 'clean', 'coffee', 'uglify' ]
