#! /usr/bin/env python

import os
import git

VERSION = '0.0000001' # HAHA!
# TODO: move this to a json file dependencies.json maybe???
deps = {
    'path': './dependencies',
    'libs': [
        {'name': 'sbt-scalabuff', 'git': 'git@github.com:geeksville/sbt-scalabuff.git', 'branch': ''},
        {'name': 'json4s', 'git': 'git@github.com:geeksville/json4s.git', 'branch': 'fixes_for_dronehub'},
        {'name': 'scalatra', 'git': 'git@github.com:geeksville/scalatra.git', 'branch': '2.3.x_2.10'}
    ]
}

class debug:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    def header(self, text):
        return self.HEADER + text + self.ENDC
    def okblue(self, text):
        return self.OKBLUE + text + self.ENDC
    def okgreen(self, text):
        return self.OKGREEN + text + self.ENDC
    def warn(self, text):
        return self.WARNING + text + self.ENDC
    def fail(self, text):
        return self.FAIL + text + self.ENDC

# TODO: python we need singletons!!!!!!!!
d = debug()

def init():
    # TODO: probably theres a better way to go about running things here
    os.system('git submodule update --recursive --init')

    print d.header('[droneapi] Dependency Tool v{}'.format(VERSION))
    if not os.path.exists(deps['path']):
        print d.warn('-- Creating dependency directory at:') + ' ' + d.okgreen(deps['path'])
        os.mkdir(deps['path'])
    build_libs()

def build_libs():
    for l in deps['libs']:
        print d.header('-- {}'.format(l['name']))
        full_lib_path = deps['path'] + '/' + l['name']
        if not os.path.exists(full_lib_path):
            print d.okblue('-- git clone {} '.format(l['git'])) + ' ' + d.okgreen(full_lib_path)
            print d.warn('(this might take a while)')
            repo = git.Repo.clone_from(l['git'], full_lib_path)
            if l['branch']:
                print d.okblue('-- checkout out branch:') + ' ' + d.okgreen(l['branch'])
                repo.git.checkout(l['branch'])
        else:
            print d.okblue('-- repo already cloned')
            repo = git.Repo(full_lib_path)

        print d.okblue('-- git pull')
        repo.git.pull()

        print d.okblue('-- running sbt publishLocal')
        print d.warn('(this might take a while)')
        # TODO: probably theres a better way to go about running things here
        os.system("cd {} && sbt publishLocal".format(full_lib_path))

if __name__ == '__main__':
    init()
