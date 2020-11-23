# Bootstrap

This needs Jekyll installed - I did it with rbenv, bundler and much cursing.

## 2020-11-23

Had some issue with ssl not loading on Catalina

```
$ ./serve.sh 
bundler: failed to load command: jekyll (/Users/duncan/.rbenv/versions/2.6.1/bin/jekyll)
LoadError: dlopen(/Users/duncan/.rbenv/versions/2.6.1/lib/ruby/2.6.0/x86_64-darwin18/digest/sha1.bundle, 9): Library not loaded: /usr/local/opt/openssl/lib/libssl.1.0.0.dylib
 
``` 

I installed a new ruby with 

`brew install ruby` (got 2.7.2) 

Installed it into rbenv with 

`ln -s /usr/local/Cellar/ruby/2.7.2 .rbenv/versions/2.7.2`

Bootstrapped with 

```
cd site
bundle install
``` 

That downloaded jekyll gems and got things running

# Build

```
eval "$(rbenv init -)"

./serve.sh
```

# Publish

```
../publish.sh
```