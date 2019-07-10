# Maintainer: Dwight Guth <dwight.guth@runtimeverification.com>
pkgname=kframework
pkgver=5.0.0
pkgrel=1
epoch=
pkgdesc="K framework toolchain. Includes K Framework compiler for K language definitions, and K interpreter and prover for programs written in languages defined in K."
arch=('x86_64')
url="https://github.com/kframework/k"
license=('custom')
groups=()
depends=('java-runtime' 'flex' 'gcc' 'gmp' 'mpfr' 'z3' 'clang' 'libyaml' 'jemalloc' 'opam' 'gawk' 'make' 'diffutils' 'patch' 'tar' 'grep' 'llvm' 'lld')
makedepends=('maven' 'jdk8-openjdk' 'cmake' 'boost' 'zlib' 'stack' 'pkg-config' 'bison' 'python')
checkdepends=()
optdepends=()
provides=()
conflicts=()
replaces=()
backup=()
options=(!strip)
install=kframework.install
changelog=
source=()
noextract=()
md5sums=()
validpgpkeys=()

prepare() {
    cd ..
    git submodule update --init --recursive
    export CARGO_HOME=$(pwd)/.cargo
    export RUSTUP_HOME=$(pwd)/.rustup
    ./llvm-backend/src/main/native/llvm-backend/install-rust
}

build() {
    cd ..
    export CARGO_HOME=$(pwd)/.cargo
    export RUSTUP_HOME=$(pwd)/.rustup
    export PATH=$CARGO_HOME/bin:$PATH
    mvn package -DskipTests
}

package() {
    cd ..
    DESTDIR="$pkgdir/usr/" src/main/scripts/package
    install -Dm644 LICENSE.md "$pkgdir/usr/share/licenses/$pkgname/LICENSE"
}
