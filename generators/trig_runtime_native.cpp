// SPDX-License-Identifier: GPL-3.0-or-later
// Standalone sanitizer replay of every recorded Java Math numeric result.
#include <java25_trig.h>
#include <bit>
#include <cfenv>
#include <cmath>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
static std::uint64_t Bits(double v)
{
    return std::isnan(v) ? UINT64_C(0x7ff8000000000000) : std::bit_cast<std::uint64_t>(v);
}
int main(int argc, char** argv)
{
    if (argc != 2 || std::fegetround() != FE_TONEAREST) return 2;
    std::ifstream file(argv[1]); if (!file) return 3;
    std::string line; std::size_t count=0;
    while (std::getline(file,line))
    {
        std::istringstream in(line); std::string kind,id,separator;
        std::uint64_t x,s,c,ss,sc,ns,nc;
        in >> kind >> id >> std::hex >> x >> separator >> s >> c >> ss >> sc >> ns >> nc;
        if (!in || kind!="T" || separator!="|") return 4;
        auto actualS=Bits(FREEROUTING_JAVA25_MATH::Sin(std::bit_cast<double>(x)));
        auto actualC=Bits(FREEROUTING_JAVA25_MATH::Cos(std::bit_cast<double>(x)));
        if(actualS!=s || actualC!=c)
        {
            std::cout << "first mismatch input=" << id << " x=" << std::hex << x
                      << " expected=" << s << "," << c << " actual=" << actualS << "," << actualC << '\n';
            return 1;
        }
        ++count;
    }
    std::cout << "{\"cases\":" << count << ",\"mismatches\":0,\"first_divergence\":null}\n";
    return count==1011501 ? 0 : 5;
}
